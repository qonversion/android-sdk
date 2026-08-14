package com.qonversion.android.sdk.internal.remoteconfig

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

private const val POLL_INTERVAL_MILLIS = 10L
private const val PROJECT_TOKEN = "project-token"
private const val SESSION_TOKEN = "qrcs1.session"
private const val SEEDED_SESSION_TOKEN = "qrcs1.seeded-session"
// Long enough to outlive the clock jumps the staleness tests perform, so an expired session can
// never be mistaken for the pruning behaviour under test.
private const val SESSION_LIFETIME_MILLIS = 90L * 24 * 60 * 60 * 1_000
private const val OCCURRED_AT_SECONDS = 1_700_000_000L
private const val LATER_OCCURRED_AT_SECONDS = 1_700_000_900L
private const val RELEASE_7 = 7L
private const val RELEASE_9 = 9L
private const val HTTP_BAD_REQUEST = 400
private const val RETRY_SCRIPT_SIZE = 8
private const val TEST_FLUSH_THRESHOLD = 3
private const val TEST_MAX_BATCH_EVENTS = 4

/**
 * Client telemetry, end to end over a real [MockWebServer]: the shipped
 * [RemoteConfigGatewayTransport] under the shipped [RemoteConfigTelemetrySender].
 *
 * Only four things are doubles, each for determinism: the durable store is in memory, the retry and
 * tick scheduler is manual, the jitter source is fixed, and the worker executor is manual — the
 * last one is not a convenience but the point of several tests, because it is what makes "the read
 * path only enqueues" observable.
 */
@Suppress("LargeClass")
internal class RemoteConfigTelemetrySenderTest {
    private lateinit var server: MockWebServer
    /**
     * One HTTP client per sender, deliberately.
     *
     * A "process restart" test binds a second sender while the first one's batch is still parked
     * on the wire; sharing a pooled connection would make the second request queue behind the
     * first one's unwritten response instead of reaching the gateway.
     */
    private val clients = Collections.synchronizedList(mutableListOf<OkHttpClient>())
    private lateinit var gateway: ScriptedGateway
    private lateinit var store: InMemoryTelemetryStore
    private lateinit var sessionStore: InMemorySessionStore
    private lateinit var scheduler: ManualScheduler
    private lateinit var worker: ManualExecutor
    private var identityScope: RemoteConfigSnapshotScope? = SCOPE_A
    private val clock = MutableTelemetryClock(OCCURRED_AT_SECONDS * 1_000)

    @Before
    fun setUp() {
        server = MockWebServer()
        gateway = ScriptedGateway()
        server.dispatcher = gateway
        server.start()
        store = InMemoryTelemetryStore()
        scheduler = ManualScheduler()
        worker = ManualExecutor()
        identityScope = SCOPE_A
        // Telemetry can only follow a read, so the realistic starting state is a session this
        // installation already holds — which is also what licenses the single re-bootstrap on 401.
        sessionStore = InMemorySessionStore()
        listOf(SCOPE_A, SCOPE_B).forEach { scope ->
            sessionStore.save(
                RemoteConfigSessionKey(scope, scope.canonicalUserId),
                RemoteConfigGatewaySession(
                    token = SEEDED_SESSION_TOKEN,
                    projectId = RC_PROJECT_ID,
                    environment = "prod",
                    expiresAtMillis = clock.now + SESSION_LIFETIME_MILLIS,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        // A test that models a crash leaves a batch parked forever; the gateway cannot shut down
        // while a dispatcher thread is still holding one.
        gateway.releaseAll()
        server.shutdown()
        clients.forEach { open ->
            open.dispatcher().executorService().shutdownNow()
            open.connectionPool().evictAll()
        }
    }

    @Test
    fun `a telemetry batch matches the gateway contract exactly`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(1)

        val batch = gateway.batches.single()
        assertEquals("POST", batch.method)
        assertEquals("/$REMOTE_CONFIG_TELEMETRY_PATH", batch.path)
        assertEquals("Bearer $PROJECT_TOKEN", batch.authorization)
        assertEquals("application/json; charset=utf-8", batch.contentType)
        assertEquals(SEEDED_SESSION_TOKEN, batch.sessionHeader)
        assertEquals(
            "{\"events\":[{\"kind\":\"decode_failure\",\"logical_key\":\"paywall_prices\"," +
                "\"release_number\":$RELEASE_7,\"count\":1,\"last_occurred_at\":$OCCURRED_AT_SECONDS}]}",
            batch.body,
        )
        assertEquals(0, sender.droppedEventCount)
    }

    @Test
    fun `repeated occurrences of the same key coalesce into one counted event`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        repeat(3) { sender.recordDecodeFailure("paywall_prices", RELEASE_7) }
        clock.now = LATER_OCCURRED_AT_SECONDS * 1_000
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        assertEquals(1, sender.pendingEntryCount)
        flush(sender)
        awaitBatches(1)

        // One event, four occurrences, stamped with the LAST one.
        assertEquals(
            "{\"events\":[{\"kind\":\"decode_failure\",\"logical_key\":\"paywall_prices\"," +
                "\"release_number\":$RELEASE_7,\"count\":4," +
                "\"last_occurred_at\":$LATER_OCCURRED_AT_SECONDS}]}",
            gateway.batches.single().body,
        )
    }

    @Test
    fun `the same key across a release rollover stays exactly one event`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        clock.now = LATER_OCCURRED_AT_SECONDS * 1_000
        // A new release rolled out between two flushes and still fails to decode.
        sender.recordDecodeFailure("paywall_prices", RELEASE_9)

        // The gateway refuses a batch carrying two events for one (kind, logical_key), so a
        // rollover must never be able to produce that pair.
        assertEquals(1, sender.pendingEntryCount)
        flush(sender)
        awaitBatches(1)
        // Counts add, and the NEWEST release wins: that is the one the dashboard has to act on.
        assertEquals(
            "{\"events\":[{\"kind\":\"decode_failure\",\"logical_key\":\"paywall_prices\"," +
                "\"release_number\":$RELEASE_9,\"count\":3," +
                "\"last_occurred_at\":$LATER_OCCURRED_AT_SECONDS}]}",
            gateway.batches.single().body,
        )
    }

    @Test
    fun `different keys stay different events`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        sender.recordDecodeFailure("onboarding", RELEASE_7)

        assertEquals(2, sender.pendingEntryCount)
        flush(sender)
        awaitBatches(1)
        assertEquals(2, gateway.batches.single().eventCount())
    }

    @Test
    fun `a guard event never carries a logical key`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.record(RemoteConfigReadGuardEvent.ReadBeforeActivate)
        flush(sender)
        awaitBatches(1)

        // The gateway rejects the whole batch when a non-decode kind names a key, so the field must
        // be absent rather than empty.
        val body = gateway.batches.single().body
        assertFalse(body.contains("logical_key"))
        assertEquals(
            "{\"events\":[{\"kind\":\"read_before_activate\",\"release_number\":0," +
                "\"count\":1,\"last_occurred_at\":$OCCURRED_AT_SECONDS}]}",
            body,
        )
    }

    @Test
    fun `every read guard event maps onto the closed wire enum`() {
        val sender = sender(maxBatchEvents = REMOTE_CONFIG_TELEMETRY_MAX_BATCH_EVENTS)
        sender.bind(SCOPE_A)

        RemoteConfigReadGuardEvent.values().forEach(sender::record)
        flush(sender)
        awaitBatches(1)

        // Six guard events are five kinds because PreloadNotReady is DROPPED, not folded: the
        // contract forbids reporting "nothing persisted yet" as a preload failure.
        assertEquals(
            listOf(
                "read_before_activate",
                "implicit_activation",
                "preload_failed",
                "preload_corrupt",
                "activation_persistence_failed",
            ),
            gateway.batches.single().kinds(),
        )
    }

    @Test
    fun `the ordinary first-launch preload state is not reported at all`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.record(RemoteConfigReadGuardEvent.PreloadNotReady)

        // "Nothing persisted yet" is what EVERY fresh install looks like. Reporting it as
        // preload_failed would make the dashboard's alarm metric fire for every new user.
        assertEquals(0, sender.pendingEntryCount)
        flush(sender)
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
        // Not a drop either: it was never an event.
        assertEquals(0, sender.droppedEventCount)
    }

    @Test
    fun `a genuine preload failure is still reported`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.record(RemoteConfigReadGuardEvent.PreloadFailed)
        flush(sender)
        awaitBatches(1)

        assertEquals(listOf("preload_failed"), gateway.batches.single().kinds())
    }

    @Test
    fun `a stale event is pruned instead of poisoning the batch`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("ancient", RELEASE_7)

        // The device came back a month later, and a healthy event happened on the way.
        clock.now = (OCCURRED_AT_SECONDS + REMOTE_CONFIG_TELEMETRY_MAX_AGE_SECONDS + 1) * 1_000
        sender.recordDecodeFailure("fresh", RELEASE_7)
        flush(sender)
        awaitBatches(1)

        // A 400 is terminal for the WHOLE batch, so the out-of-window event must never travel.
        assertEquals(listOf("fresh"), gateway.batches.single().logicalKeys())
        assertEquals(1, sender.droppedEventCount)
    }

    @Test
    fun `a future-skewed event is pruned instead of poisoning the batch`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        // A device whose clock is running ahead of the server's window.
        clock.now = (OCCURRED_AT_SECONDS + REMOTE_CONFIG_TELEMETRY_MAX_SKEW_SECONDS + 60) * 1_000
        sender.recordDecodeFailure("from_the_future", RELEASE_7)

        clock.now = OCCURRED_AT_SECONDS * 1_000
        sender.recordDecodeFailure("fresh", RELEASE_7)
        flush(sender)
        awaitBatches(1)

        assertEquals(listOf("fresh"), gateway.batches.single().logicalKeys())
        assertEquals(1, sender.droppedEventCount)
    }

    @Test
    fun `a flush never bootstraps a session and keeps its events buffered`() {
        sessionStore.clear(RemoteConfigSessionKey(SCOPE_A, SCOPE_A.canonicalUserId))
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)

        // Session establishment belongs to the config read path: a diagnostic signal must not be
        // the reason an installation contacts the gateway at all.
        assertTrue(gateway.sessions.isEmpty())
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
        // Not an attempt: no retry budget spent, nothing dropped, everything still owed.
        assertEquals(1, sender.pendingEntryCount)
        assertEquals(0, sender.droppedEventCount)
        assertEquals(emptyList<Long>(), retryDelays())
    }

    @Test
    fun `a counter-only bump never reaches for storage`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()
        assertEquals(1, store.load(SCOPE_A).single().count)

        // A disk write per config read is not acceptable on a path the app can drive; losing a few
        // increments to a crash is a rounding error in an aggregate metric.
        repeat(10) { sender.recordDecodeFailure("paywall_prices", RELEASE_7) }
        worker.runAll()
        assertEquals(1, store.load(SCOPE_A).single().count)

        // The increments were not lost, they were simply never written: the in-memory bucket still
        // reports all eleven occurrences.
        flush(sender)
        awaitBatches(1)
        assertEquals(11, gateway.batches.single().count())
    }

    @Test
    fun `a counter-only bump at or above the flush threshold still never reaches for storage`() {
        // The regression this pins: with the threshold merely TESTED rather than latched, every
        // bump past the tenth bucket scheduled a drain — a synchronous preferences commit on the
        // single worker the snapshot preloader and the manager also run on. Telemetry would starve
        // the config path through its own queue.
        // Parked so the batch stays in flight and the buffer stays exactly at the threshold: this
        // test is about what a counter bump costs, not about what a settled batch costs.
        gateway.parkBatchesOn(CountDownLatch(1))
        val sender = sender()
        sender.bind(SCOPE_A)
        repeat(TEST_FLUSH_THRESHOLD) { index -> sender.recordDecodeFailure("key-$index", RELEASE_7) }
        worker.runAll()
        awaitBatches(1)
        val writesAtThreshold = store.writeCount

        repeat(20) { sender.recordDecodeFailure("key-0", RELEASE_7) }
        worker.runAll()

        assertEquals(writesAtThreshold, store.writeCount)
        assertEquals(TEST_FLUSH_THRESHOLD, sender.pendingEntryCount)
    }

    @Test
    fun `an unchanged buffer is never rewritten`() {
        gateway.parkBatchesOn(CountDownLatch(1))
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()
        val writesAfterFirstEntry = store.writeCount

        // Repeated drains of a buffer that did not change shape must cost nothing.
        repeat(5) {
            sender.onSuccessfulFetch()
            worker.runAll()
        }

        assertEquals(writesAfterFirstEntry, store.writeCount)
    }

    @Test
    fun `a resumed buffer is not rewritten by the bind that resumed it`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()
        gateway.parkBatchesOn(CountDownLatch(1))

        // A second process over the same durable state: it already holds exactly what was loaded.
        val restarted = sender()
        restarted.bind(SCOPE_A)
        val writesAfterBind = store.writeCount
        worker.runAll()

        assertEquals(writesAfterBind, store.writeCount)
    }

    @Test
    fun `a record during a slow bind does not block on storage`() {
        val loading = CountDownLatch(1)
        val loadStarted = CountDownLatch(1)
        store.blockLoadsOn(loadStarted, loading)
        val sender = sender()
        val binder = Thread { sender.bind(SCOPE_A) }.apply { start() }
        assertTrue("the bind never reached storage", loadStarted.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))

        // The read path may not queue behind an identity change reading and parsing up to 64 KiB.
        val recorded = Thread { sender.recordDecodeFailure("paywall_prices", RELEASE_7) }.apply { start() }
        recorded.join(TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS))
        val blocked = recorded.isAlive
        loading.countDown()
        binder.join(TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS))
        recorded.join(TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS))

        assertFalse("a config read blocked on the telemetry store", blocked)
    }

    @Test
    fun `a fetch policy persistence failure is reported as a persistence failure`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordPolicyPersistenceFailure()
        flush(sender)
        awaitBatches(1)

        assertEquals(listOf("activation_persistence_failed"), gateway.batches.single().kinds())
    }

    @Test
    fun `a guard event produced before the first bind is delivered after it`() {
        val sender = sender()

        // The read guard reports each of its events ONCE per process. A read_before_activate that
        // happens between SDK construction and the first identify has no second chance, so dropping
        // it here would systematically under-report the metric the panel exists for.
        sender.record(RemoteConfigReadGuardEvent.ReadBeforeActivate)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
        assertEquals(0, sender.pendingEntryCount)

        sender.bind(SCOPE_A)
        worker.runAll()
        awaitBatches(1)

        assertEquals(listOf("read_before_activate", "decode_failure"), gateway.batches.single().kinds())
        assertEquals(0, sender.droppedEventCount)
    }

    @Test
    fun `the pre-bind buffer is bounded and counts what it drops`() {
        val sender = sender(maxPreBindEvents = 4)

        repeat(10) { index -> sender.recordDecodeFailure("key-$index", RELEASE_7) }
        sender.bind(SCOPE_A)
        worker.runAll()
        awaitBatches(1)

        // Drop-oldest: the four newest survive, and the six lost occurrences are MEASURED rather
        // than silently discarded.
        assertEquals(listOf("key-6", "key-7", "key-8", "key-9"), gateway.batches.single().logicalKeys())
        assertEquals(6, sender.droppedEventCount)
    }

    @Test
    fun `an unbindable sender never grows past the pre-bind bound`() {
        val sender = sender(maxPreBindEvents = 4)

        repeat(1_000) { index -> sender.recordDecodeFailure("key-$index", RELEASE_7) }

        assertEquals(0, sender.pendingEntryCount)
        assertEquals(996, sender.droppedEventCount)
    }

    @Test
    fun `pruning a stale buffer clears the durable record instead of leaving it immortal`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("ancient", RELEASE_7)
        worker.runAll()
        assertEquals(1, store.load(SCOPE_A).size)

        // A month later, on a new process over the same durable state.
        clock.now = (OCCURRED_AT_SECONDS + REMOTE_CONFIG_TELEMETRY_MAX_AGE_SECONDS + 1) * 1_000
        val restarted = sender()
        restarted.bind(SCOPE_A)
        worker.runAll()

        // Without a durable prune the record survives every restart and re-inflates the counter
        // once per process, forever.
        assertTrue("the all-stale record outlived its prune", store.load(SCOPE_A).isEmpty())
        assertEquals(1, restarted.droppedEventCount)
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)

        val secondRestart = sender()
        secondRestart.bind(SCOPE_A)
        worker.runAll()
        assertEquals(0, secondRestart.droppedEventCount)
    }

    @Test
    fun `an untrusted clock suspends pruning and flushing instead of discarding the buffer`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()

        // A device whose RTC has not been set yet. Believing it would make every buffered event
        // look future-skewed and throw the whole buffer away.
        clock.now = 0
        flush(sender)

        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
        assertEquals(1, sender.pendingEntryCount)
        assertEquals(0, sender.droppedEventCount)
        assertEquals(1, store.load(SCOPE_A).size)

        // Once the clock is real again the buffer is delivered, not mourned.
        clock.now = OCCURRED_AT_SECONDS * 1_000
        flush(sender)
        awaitBatches(1)
    }

    @Test
    fun `a permanently refused identity is never sent again`() {
        gateway.scriptTelemetry(HTTP_BAD_REQUEST)
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(1)
        awaitDropped(sender, 1)

        // A 400 is deterministic for a given (kind, logical_key). Re-sending it every tick would be
        // an infinite request loop that also poisons every healthy event travelling with it.
        repeat(5) { sender.recordDecodeFailure("paywall_prices", RELEASE_7) }
        flush(sender)
        scheduler.runAll()
        worker.runAll()

        assertEquals(1, gateway.batches.size)
        assertEquals(0, sender.pendingEntryCount)
        assertEquals(6, sender.droppedEventCount)
    }

    @Test
    fun `occurrences accrued while a refused batch was on the wire do not survive it`() {
        val onTheWire = CountDownLatch(1)
        gateway.parkBatchesOn(onTheWire)
        gateway.scriptTelemetry(HTTP_BAD_REQUEST)
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(1)

        repeat(2) { sender.recordDecodeFailure("paywall_prices", RELEASE_7) }
        gateway.parkBatchesOn(null)
        onTheWire.countDown()

        awaitDropped(sender, 3)
        // The whole bucket goes, not just the reported count: a residue would re-flush on the very
        // next tick and be refused again.
        awaitBuffer { it.isEmpty() }
        scheduler.runAll()
        worker.runAll()
        assertEquals(1, gateway.batches.size)
    }

    @Test
    fun `a key that is not valid UTF-8 is refused locally`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        // An unpaired high surrogate: `toByteArray` would transcode it to '?' silently, so a
        // byte-level check alone would send a key the app never read.
        sender.recordDecodeFailure("paywall\uD83Dprices", RELEASE_7)
        // 150 emoji are 150 UTF-16 units but 600 UTF-8 bytes: the server bounds the BYTES.
        sender.recordDecodeFailure("😀".repeat(150), RELEASE_7)

        assertEquals(0, sender.pendingEntryCount)
        assertEquals(2, sender.droppedEventCount)
    }

    @Test
    fun `a key at the byte budget is still accepted`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        // 50 four-byte emoji are exactly 200 bytes: the bound is inclusive, on bytes.
        sender.recordDecodeFailure("😀".repeat(50), RELEASE_7)

        assertEquals(1, sender.pendingEntryCount)
        assertEquals(0, sender.droppedEventCount)
    }

    @Test
    fun `a batch that crashed mid-flight is redelivered by the next process`() {
        // Only the FIRST batch is parked, and it is never released: that is what a process death
        // between "the POST left" and "the gateway answered" actually looks like.
        gateway.parkBatchesOn(CountDownLatch(1), limit = 1)
        val crashed = sender()
        crashed.bind(SCOPE_A)
        crashed.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(crashed)
        awaitBatches(1)

        // It was persisted BEFORE it was dispatched — an in-flight batch is only removed from the
        // buffer when the gateway answers — so the durable record still holds it.
        assertEquals(1, store.load(SCOPE_A).size)

        worker = ManualExecutor()
        scheduler = ManualScheduler()
        val restarted = sender()
        restarted.bind(SCOPE_A)
        worker.runAll()

        awaitBatches(2)
        // Redelivered whole: at-least-once is the pinned delivery semantic, loss is not.
        assertEquals(1, gateway.batches[1].count())
        awaitBuffer { it.isEmpty() }
    }

    @Test
    fun `reaching the distinct entry threshold flushes without waiting for a tick`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        repeat(TEST_FLUSH_THRESHOLD - 1) { index -> sender.recordDecodeFailure("key-$index", RELEASE_7) }
        worker.runAll()
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)

        sender.recordDecodeFailure("key-last", RELEASE_7)
        worker.runAll()

        awaitBatches(1)
        assertEquals(TEST_FLUSH_THRESHOLD, gateway.batches.single().eventCount())
    }

    @Test
    fun `a buffer below the threshold is delivered by the periodic tick`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)

        // The tick is armed by the first buffered event, at the contract's 30 s.
        assertEquals(listOf(REMOTE_CONFIG_TELEMETRY_TICK_MILLIS), scheduler.requestedDelays)
        scheduler.runAll()
        worker.runAll()

        awaitBatches(1)
    }

    @Test
    fun `an idle sender arms no timer at all`() {
        val sender = sender()

        sender.bind(SCOPE_A)

        // A dormant integration must not wake a thread every 30 seconds for an empty batch.
        assertEquals(emptyList<Long>(), scheduler.requestedDelays)
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun `a fetch the gateway answered flushes opportunistically`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)

        sender.onSuccessfulFetch()
        worker.runAll()

        awaitBatches(1)
    }

    @Test
    fun `distinct entries beyond the bound are dropped instead of growing the map`() {
        val sender = sender(maxEntries = REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES)
        sender.bind(SCOPE_A)

        repeat(REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES + 5) { index ->
            sender.recordDecodeFailure("key-$index", RELEASE_7)
        }

        assertEquals(REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES, sender.pendingEntryCount)
        assertEquals(5, sender.droppedEventCount)
        // An already-known key still counts up: the bound is on distinct entries, not occurrences.
        sender.recordDecodeFailure("key-0", RELEASE_7)
        assertEquals(REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES, sender.pendingEntryCount)
        assertEquals(5, sender.droppedEventCount)
    }

    @Test
    fun `an event the gateway could never accept is dropped at the source`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        // Over the 200-byte logical key budget: queueing it would cost every other event in the
        // same POST a terminal 400.
        sender.recordDecodeFailure("k".repeat(REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES + 1), RELEASE_7)

        assertEquals(0, sender.pendingEntryCount)
        assertEquals(1, sender.droppedEventCount)
    }

    @Test
    fun `a batch is capped and the remainder follows immediately`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        repeat(TEST_MAX_BATCH_EVENTS + 2) { index -> sender.recordDecodeFailure("key-$index", RELEASE_7) }
        flush(sender)

        awaitBatches(2)
        assertEquals(TEST_MAX_BATCH_EVENTS, gateway.batches[0].eventCount())
        assertEquals(2, gateway.batches[1].eventCount())
        awaitBuffer { it.isEmpty() }
    }

    @Test
    fun `a 400 drops the batch permanently`() {
        gateway.scriptTelemetry(HTTP_BAD_REQUEST)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(1)

        // Terminal: never retried, and the buffer is cleared rather than left to be refused again.
        awaitDropped(sender, 1)
        awaitBuffer { it.isEmpty() }
        assertEquals(0, sender.pendingEntryCount)
        scheduler.runAll()
        worker.runAll()
        assertEquals(1, gateway.batches.size)
    }

    @Test
    fun `a 401 re-bootstraps exactly once and retries the batch`() {
        gateway.scriptTelemetry(HTTP_UNAUTHORIZED)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(2)

        assertEquals(1, gateway.sessions.size)
        assertEquals(listOf(SEEDED_SESSION_TOKEN, SESSION_TOKEN), gateway.batches.map { it.sessionHeader })
        assertEquals(0, sender.droppedEventCount)
        // No retry was scheduled: the re-bootstrap is the transport's business, not the sender's
        // retry budget.
        assertEquals(emptyList<Long>(), scheduler.requestedDelays.filter { it != REMOTE_CONFIG_TELEMETRY_TICK_MILLIS })
    }

    @Test
    fun `a 401 from the telemetry route never forgets the shared session`() {
        gateway.scriptTelemetry(HTTP_UNAUTHORIZED, HTTP_UNAUTHORIZED)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(2)
        awaitDropped(sender, 1)

        // The stored session belongs to the config read path; an out-of-band signal may not
        // invalidate it. The re-bootstrap replaced it, it was never cleared.
        val stored = sessionStore.load(RemoteConfigSessionKey(SCOPE_A, SCOPE_A.canonicalUserId))
        assertNotNull("the telemetry 401 forgot the config read path's session", stored)
        assertEquals(SESSION_TOKEN, stored?.token)
    }

    @Test
    fun `a 503 is retried to the attempt bound and then abandoned for the process`() {
        gateway.scriptTelemetry(*IntArray(RETRY_SCRIPT_SIZE) { HTTP_SERVICE_UNAVAILABLE })
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)

        runRetryLadder()

        assertEquals(REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS, gateway.batches.size)
        // Half the cap plus fixed jitter, doubling: 750, 1500.
        assertEquals(listOf(750L, 1_500L), retryDelays())
        scheduler.runAll()
        worker.runAll()
        assertEquals(REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS, gateway.batches.size)
        // Abandoned in this process, still owed: the durable buffer is what a later start picks up.
        awaitBuffer { it.size == 1 }
    }

    @Test
    fun `an exhausted ladder is not re-armed by a re-binding`() {
        gateway.scriptTelemetry(*IntArray(RETRY_SCRIPT_SIZE) { HTTP_SERVICE_UNAVAILABLE })
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        runRetryLadder()

        // An identify that lands on the same identity, and a new event for it.
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("onboarding", RELEASE_7)
        flush(sender)

        assertEquals(REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS, gateway.batches.size)
    }

    @Test
    fun `a buffered batch survives a process restart`() {
        gateway.scriptTelemetry(*IntArray(RETRY_SCRIPT_SIZE) { HTTP_SERVICE_UNAVAILABLE })
        val crashed = sender()
        crashed.bind(SCOPE_A)
        crashed.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(crashed)
        runRetryLadder()

        // A new process: new sender, new scheduler, new worker, same durable store.
        gateway.clearScript()
        scheduler = ManualScheduler()
        worker = ManualExecutor()
        val restarted = sender()
        restarted.bind(SCOPE_A)
        worker.runAll()

        awaitBatches(REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS + 1)
        // The event still reports when it OCCURRED, not when it was finally delivered.
        assertEquals(
            "{\"events\":[{\"kind\":\"decode_failure\",\"logical_key\":\"paywall_prices\"," +
                "\"release_number\":$RELEASE_7,\"count\":1,\"last_occurred_at\":$OCCURRED_AT_SECONDS}]}",
            gateway.batches.last().body,
        )
        awaitBuffer { it.isEmpty() }
    }

    @Test
    fun `a batch is never sent under another identity's session`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        // The transport now addresses another identity than the one the events were buffered for.
        identityScope = SCOPE_B

        flush(sender)

        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
        // Not an attempt: no retry budget was spent and the buffer stays queued.
        assertEquals(0, sender.droppedEventCount)
        assertEquals(1, sender.pendingEntryCount)
        assertEquals(emptyList<Long>(), retryDelays())
    }

    @Test
    fun `binding another identity drops the previous identity's buffer from memory`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        worker.runAll()

        identityScope = SCOPE_B
        sender.bind(SCOPE_B)

        assertEquals(0, sender.pendingEntryCount)
        // Still owed by the identity that produced it, and only by that one.
        assertEquals(1, store.load(SCOPE_A).size)
        flush(sender)
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
    }

    @Test
    fun `occurrences that arrive while a batch is on the wire are not lost`() {
        val onTheWire = CountDownLatch(1)
        gateway.parkBatchesOn(onTheWire)
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)
        awaitBatches(1)

        // The same key fails twice more while the first batch is still unanswered.
        repeat(2) { sender.recordDecodeFailure("paywall_prices", RELEASE_7) }
        gateway.parkBatchesOn(null)
        onTheWire.countDown()

        // Delivering a batch leaves a non-empty buffer, so the remainder follows on its own.
        awaitBatches(2)
        // The delivered count is subtracted, not cleared: 3 - 1 = 2 still to report.
        assertEquals(2, gateway.batches[1].count())
        awaitBuffer { it.isEmpty() }
    }

    @Test
    fun `recording never touches the durable store on the caller's thread`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        sender.record(RemoteConfigReadGuardEvent.ReadBeforeActivate)

        // Nothing has run on the worker yet, so nothing can have been written or sent.
        assertEquals(emptyList<RecordedBatch>(), gateway.batches)
        assertTrue(store.load(SCOPE_A).isEmpty())
        assertEquals(2, sender.pendingEntryCount)
        worker.runAll()
        assertEquals(2, store.load(SCOPE_A).size)
    }

    @Test
    fun `a durable write that fails never costs an in-memory event`() {
        store.failWrites = true
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)
        flush(sender)

        awaitBatches(1)
        assertEquals(1, gateway.batches.single().eventCount())
        assertEquals(0, sender.droppedEventCount)
    }

    @Test
    fun `a rejected worker leaves the buffer intact for the next attempt`() {
        worker.reject = true
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordDecodeFailure("paywall_prices", RELEASE_7)

        assertEquals(1, sender.pendingEntryCount)
        assertEquals(0, sender.droppedEventCount)
        worker.reject = false
        flush(sender)
        awaitBatches(1)
    }

    private fun sender(
        maxEntries: Int = REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES,
        maxBatchEvents: Int = TEST_MAX_BATCH_EVENTS,
        maxPreBindEvents: Int = REMOTE_CONFIG_TELEMETRY_MAX_PRE_BIND_EVENTS,
    ) = RemoteConfigTelemetrySender(
        transport = transport(),
        store = store,
        clock = clock,
        random = { 0.5 },
        scheduler = scheduler,
        executor = worker,
        maxEntries = maxEntries,
        flushThreshold = TEST_FLUSH_THRESHOLD,
        maxBatchEvents = maxBatchEvents,
        maxPreBindEvents = maxPreBindEvents,
    )

    private fun transport() = RemoteConfigGatewayTransport(
        callFactory = OkHttpClient().also(clients::add),
        baseUrlProvider = { server.url("/").toString() },
        identityProvider = {
            identityScope?.let { scope ->
                RemoteConfigTransportIdentity(scope, PROJECT_TOKEN, scope.canonicalUserId)
            }
        },
        clientContextProvider = { null },
        sessionStore = sessionStore,
        projectIds = RemoteConfigProjectIdRegistry(InMemoryProjectIdStore()),
        clock = clock,
        moshi = Moshi.Builder().build(),
        logger = SilentLogger(),
    )

    /** Asks for a flush and lets the worker take it, the way the shipped wiring does. */
    private fun flush(sender: RemoteConfigTelemetrySender) {
        sender.onSuccessfulFetch()
        worker.runAll()
    }

    /** Walks the bounded retry ladder to its end, without racing the timer it arms. */
    private fun runRetryLadder() {
        repeat(REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS - 1) { attempt ->
            awaitBatches(attempt + 1)
            // Counted rather than "something is pending": the periodic tick is pending too, and
            // firing it instead of the retry would make the ladder silently stall.
            await("no retry was scheduled after attempt ${attempt + 1}") { retryDelays().size > attempt }
            scheduler.runAll()
            worker.runAll()
        }
        awaitBatches(REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS)
    }

    /** Every delay the retry ladder asked for, with the periodic tick filtered out. */
    private fun retryDelays(): List<Long> =
        scheduler.requestedDelays.filter { it != REMOTE_CONFIG_TELEMETRY_TICK_MILLIS }

    private fun awaitBatches(count: Int) =
        await("expected $count telemetry batches, saw ${gateway.batches.size}") {
            gateway.batches.size >= count
        }

    private fun awaitDropped(sender: RemoteConfigTelemetrySender, count: Long) =
        await("expected $count dropped events, saw ${sender.droppedEventCount}") {
            sender.droppedEventCount >= count
        }

    private fun awaitBuffer(predicate: (List<RemoteConfigTelemetryEvent>) -> Boolean) =
        await("durable telemetry buffer never reached the expected shape: ${store.load(SCOPE_A)}") {
            predicate(store.load(SCOPE_A))
        }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS)
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError(message)
    }

    private class MutableTelemetryClock(@Volatile var now: Long) : RemoteConfigFetchClock {
        override fun nowMillis(): Long = now
    }

    /**
     * A worker whose tasks only run when the test says so.
     *
     * This is what makes "a handler invoked from the read path only enqueues and returns"
     * observable: anything the sender does off this executor happened on the caller's thread.
     */
    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        @Volatile
        var reject: Boolean = false

        override fun execute(command: Runnable) {
            if (reject) throw RejectedExecutionException("worker is down")
            synchronized(tasks) { tasks.addLast(command) }
        }

        fun runAll() {
            while (true) {
                val next = synchronized(tasks) { tasks.pollFirst() } ?: return
                next.run()
            }
        }
    }

    private data class RecordedBatch(
        val method: String,
        val path: String?,
        val body: String,
        val sessionHeader: String?,
        val authorization: String?,
        val contentType: String?,
    ) {
        fun eventCount(): Int = KIND_PATTERN.findAll(body).count()

        fun kinds(): List<String> = KIND_PATTERN.findAll(body).map { it.groupValues[1] }.toList()

        fun logicalKeys(): List<String> = KEY_PATTERN.findAll(body).map { it.groupValues[1] }.toList()

        fun count(): Long = COUNT_PATTERN.find(body)?.groupValues?.get(1)?.toLong() ?: 0
    }

    /** Answers by path, so a telemetry batch and a bootstrap are never order-coupled. */
    private class ScriptedGateway : Dispatcher() {
        val batches: MutableList<RecordedBatch> = Collections.synchronizedList(mutableListOf())
        val sessions: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val statuses = ArrayDeque<Int>()

        @Volatile
        private var park: CountDownLatch? = null

        @Volatile
        private var parkLimit: Int = Int.MAX_VALUE

        private val parked = java.util.concurrent.atomic.AtomicInteger()

        /** Answers the next batches with [scripted], then `204` forever. */
        fun scriptTelemetry(vararg scripted: Int) {
            synchronized(statuses) { scripted.forEach(statuses::addLast) }
        }

        fun clearScript() = synchronized(statuses) { statuses.clear() }

        /**
         * Accepts a batch and holds its answer until [latch] opens, so a second batch can be
         * produced while the first one is genuinely on the wire.
         */
        fun parkBatchesOn(latch: CountDownLatch?, limit: Int = Int.MAX_VALUE) {
            parked.set(0)
            parkLimit = limit
            park = latch
        }

        /** Releases anything still parked, so a test that models a crash can still shut down. */
        fun releaseAll() {
            val parked = park
            park = null
            parked?.countDown()
        }

        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            RC_SESSION_PATH -> {
                sessions += request.body.readUtf8()
                MockResponse().setResponseCode(HTTP_OK).setBody(sessionBody(sessions.size))
            }
            "/$REMOTE_CONFIG_TELEMETRY_PATH" -> {
                batches += RecordedBatch(
                    method = request.method.orEmpty(),
                    path = request.path,
                    body = request.body.readUtf8(),
                    sessionHeader = request.getHeader(REMOTE_CONFIG_SESSION_HEADER),
                    authorization = request.getHeader("Authorization"),
                    contentType = request.getHeader("Content-Type"),
                )
                park?.takeIf { parked.getAndIncrement() < parkLimit }
                    ?.await(PARK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                MockResponse().setResponseCode(
                    synchronized(statuses) { statuses.pollFirst() } ?: HTTP_NO_CONTENT,
                )
            }
            else -> MockResponse().setResponseCode(HTTP_NOT_FOUND)
        }

        private fun sessionBody(ordinal: Int): String {
            val token = if (ordinal == 1) SESSION_TOKEN else "$SESSION_TOKEN-$ordinal"
            return "{\"session_token\":\"$token\",\"project_id\":$RC_PROJECT_ID," +
                "\"environment\":\"prod\",\"expires_at\":\"2030-01-01T00:00:00Z\"}"
        }

        private companion object {
            const val PARK_TIMEOUT_SECONDS = 5L
        }
    }

    private companion object {
        val KIND_PATTERN = Regex("\"kind\":\"([a-z_]+)\"")
        val KEY_PATTERN = Regex("\"logical_key\":\"([^\"]+)\"")
        val COUNT_PATTERN = Regex("\"count\":(\\d+)")
        val SCOPE_A = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "QON_anon_a")
        val SCOPE_B = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "QON_anon_b")
    }
}
