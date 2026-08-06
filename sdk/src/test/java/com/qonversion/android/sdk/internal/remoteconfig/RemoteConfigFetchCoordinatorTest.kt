package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteConfigFetchCoordinatorTest {
    private val scope = RemoteConfigSnapshotScope("project", "production", "canonical-user")
    private val binding = RemoteConfigFetchBinding(
        scope = scope,
        expectation = RemoteConfigSnapshotEnvelopeExpectation(
            projectId = 42,
            environmentUid = "production",
            contextFingerprint = "a".repeat(64),
        ),
    )

    @Test
    fun `concurrent fetches coalesce into one transport request`() {
        val transport = RecordingTransport()
        val coordinator = coordinator(transport)
        coordinator.transitionTo(binding)
        val results = mutableListOf<RemoteConfigFetchResult>()

        coordinator.fetch(callback = results::add)
        coordinator.fetch(forceReason = RemoteConfigFetchForceReason.Identify, callback = results::add)

        assertEquals(1, transport.requests.size)
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 400))
        assertEquals(2, results.size)
    }

    @Test
    fun `minimum interval is persisted while an explicit lifecycle fetch bypasses it`() {
        val transport = RecordingTransport()
        val clock = MutableClock(1_000)
        val policyStore = InMemoryFetchPolicyStore()
        val coordinator = coordinator(
            transport = transport,
            clock = clock,
            policyStore = policyStore,
            policy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 60_000),
        )
        coordinator.transitionTo(binding)
        coordinator.fetch(callback = {})
        transport.complete(success("first", 1))

        val gated = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = gated::add)
        assertEquals(1, transport.requests.size)
        assertTrue(gated.single() is RemoteConfigFetchResult.MinimumInterval)

        coordinator.fetch(forceReason = RemoteConfigFetchForceReason.Build, callback = {})
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `retry after persists across restart and lifecycle force does not bypass backoff`() {
        val transport = RecordingTransport()
        val clock = MutableClock(1_000)
        val policyStore = InMemoryFetchPolicyStore()
        val policy = RemoteConfigFetchPolicy(
            minimumFetchIntervalMillis = 0,
            initialBackoffMillis = 1_000,
            maximumBackoffMillis = 10_000,
        )
        val first = coordinator(transport, clock, policyStore, policy)
        first.transitionTo(binding)
        first.fetch(callback = {})
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 429, retryAfterMillis = 4_000))

        val forced = mutableListOf<RemoteConfigFetchResult>()
        first.fetch(forceReason = RemoteConfigFetchForceReason.Identify, callback = forced::add)
        assertEquals(1, transport.requests.size)
        assertEquals(5_000L, (forced.single() as RemoteConfigFetchResult.Backoff).nextAllowedAtMillis)

        val restartedTransport = RecordingTransport()
        val restarted = coordinator(restartedTransport, clock, policyStore, policy)
        restarted.transitionTo(binding)
        val beforeDeadline = mutableListOf<RemoteConfigFetchResult>()
        restarted.fetch(callback = beforeDeadline::add)
        assertTrue(beforeDeadline.single() is RemoteConfigFetchResult.Backoff)
        assertEquals(0, restartedTransport.requests.size)

        clock.now = 5_000
        restarted.fetch(callback = {})
        assertEquals(1, restartedTransport.requests.size)
    }

    @Test
    fun `identify cannot evade project environment backoff by changing canonical identity`() {
        val transport = RecordingTransport()
        val clock = MutableClock(1_000)
        val coordinator = coordinator(
            transport = transport,
            clock = clock,
            policy = RemoteConfigFetchPolicy(
                minimumFetchIntervalMillis = 0,
                initialBackoffMillis = 1_000,
                maximumBackoffMillis = 10_000,
            ),
        )
        coordinator.transitionTo(binding)
        coordinator.fetch(callback = {})
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 429, retryAfterMillis = 4_000))

        coordinator.transitionTo(
            binding.copy(
                scope = RemoteConfigSnapshotScope("project", "production", "identified-user"),
                expectation = binding.expectation.copy(contextFingerprint = "b".repeat(64)),
            ),
        )
        val result = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(forceReason = RemoteConfigFetchForceReason.Identify, callback = result::add)

        assertEquals(1, transport.requests.size)
        assertEquals(5_000L, (result.single() as RemoteConfigFetchResult.Backoff).nextAllowedAtMillis)
    }

    @Test
    fun `retryable failures use capped exponential full jitter and success resets it`() {
        val transport = RecordingTransport()
        val clock = MutableClock(1_000)
        val random = MutableRandom(0.5)
        val policyStore = InMemoryFetchPolicyStore()
        val coordinator = coordinator(
            transport = transport,
            clock = clock,
            random = random,
            policyStore = policyStore,
            policy = RemoteConfigFetchPolicy(
                minimumFetchIntervalMillis = 0,
                initialBackoffMillis = 1_000,
                maximumBackoffMillis = 1_500,
            ),
        )
        coordinator.transitionTo(binding)
        coordinator.fetch(callback = {})
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 500))
        assertEquals(
            RemoteConfigFetchPolicyState(
                consecutiveRetryableFailures = 1,
                nextAllowedFetchAtMillis = 1_500,
            ),
            policyStore.load(RemoteConfigFetchPolicyScope.from(scope)),
        )

        clock.now = 1_500
        random.value = Math.nextDown(1.0)
        coordinator.fetch(callback = {})
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 503))
        assertEquals(
            2_999L,
            policyStore.load(RemoteConfigFetchPolicyScope.from(scope))?.nextAllowedFetchAtMillis,
        )

        clock.now = 2_999
        coordinator.fetch(callback = {})
        transport.complete(success("recovered", 2))
        assertEquals(
            RemoteConfigFetchPolicyState(lastSuccessfulFetchAtMillis = 2_999),
            policyStore.load(RemoteConfigFetchPolicyScope.from(scope)),
        )
    }

    @Test
    fun `timeout serves Active without cancelling request and late response persists Candidate`() {
        val transport = RecordingTransport()
        val scheduler = ManualScheduler()
        val snapshotStore = InMemorySnapshotStore()
        val bundle = RemoteConfigScopedBundledRelease(
            projectKey = "project",
            environment = "production",
            release = release("bundle", 1, "0"),
        )
        val core = RemoteConfigSnapshotCore(snapshotStore, bundle)
        val coordinator = coordinator(
            transport = transport,
            core = core,
            scheduler = scheduler,
            policy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 0, timeoutMillis = 100),
        )
        coordinator.transitionTo(binding)
        core.acceptCandidate(scope, release("active", 1, "1"))
        core.activate()
        val results = mutableListOf<RemoteConfigFetchResult>()

        coordinator.fetch(callback = results::add)
        scheduler.runNext()

        val timedOut = results.single() as RemoteConfigFetchResult.TimedOut
        assertEquals("1", timedOut.snapshot.rawValue("a")?.value?.decodeToString())
        transport.complete(success("candidate", 2))
        assertEquals(1, results.size)
        assertEquals("1", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
        assertEquals("candidate", core.lastFetchedSnapshot()?.releaseUid)
    }

    @Test
    fun `first fetch after every waiter timed out fences zombie operation without cancelling HTTP`() {
        val transport = RecordingTransport()
        val scheduler = ManualScheduler()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(
            transport = transport,
            core = core,
            scheduler = scheduler,
            policy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 0, timeoutMillis = 100),
        )
        coordinator.transitionTo(binding)
        val first = mutableListOf<RemoteConfigFetchResult>()
        val second = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = first::add)
        scheduler.runNext()

        coordinator.fetch(forceReason = RemoteConfigFetchForceReason.Build, callback = second::add)

        assertEquals(2, transport.requests.size)
        transport.complete(success("late-zombie", 1))
        assertEquals(null, core.lastFetchedSnapshot())
        transport.complete(success("current", 2))
        assertEquals("current", core.lastFetchedSnapshot()?.releaseUid)
        assertTrue(second.single() is RemoteConfigFetchResult.Fetched)
    }

    @Test
    fun `timeout falls through to matching bundled defaults when no Active exists`() {
        val transport = RecordingTransport()
        val scheduler = ManualScheduler()
        val bundle = RemoteConfigScopedBundledRelease(
            projectKey = "project",
            environment = "production",
            release = release("bundle", 1, "0"),
        )
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundle)
        val coordinator = coordinator(
            transport = transport,
            core = core,
            scheduler = scheduler,
            policy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 0, timeoutMillis = 100),
        )
        coordinator.transitionTo(binding)
        val results = mutableListOf<RemoteConfigFetchResult>()

        coordinator.fetch(callback = results::add)
        scheduler.runNext()

        val timedOut = results.single() as RemoteConfigFetchResult.TimedOut
        assertEquals("0", timedOut.snapshot.rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `conditional fetch sends strong ETag only for an exact local canonical body`() {
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        val first = success("first", 1)
        coordinator.fetch(callback = {})
        transport.complete(first)

        val results = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = results::add)

        assertEquals(first.etag, transport.requests.last().ifNoneMatch)
        transport.complete(RemoteConfigFetchResponse.NotModified(etag = first.etag))
        assertTrue(results.single() is RemoteConfigFetchResult.NotModified)
        assertEquals("first", core.lastFetchedSnapshot()?.releaseUid)
    }

    @Test
    fun `conditional validator never falls through a noncanonical Candidate to canonical Active`() {
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        coordinator.fetch(callback = {})
        transport.complete(success("active", 1))
        core.activate()
        core.acceptCandidate(scope, release("local-candidate", 2, "2"))

        coordinator.fetch(callback = {})

        assertEquals(null, transport.requests.last().ifNoneMatch)
    }

    @Test
    fun `304 validator invalidated by an intervening head retries once without ETag`() {
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        val canonical = success("canonical", 1)
        coordinator.fetch(callback = {})
        transport.complete(canonical)
        coordinator.fetch(callback = {})
        assertEquals(canonical.etag, transport.requests.last().ifNoneMatch)

        core.acceptCandidate(scope, release("intervening", 2, "2"))
        transport.complete(RemoteConfigFetchResponse.NotModified(etag = canonical.etag))

        assertEquals(3, transport.requests.size)
        assertEquals(null, transport.requests.last().ifNoneMatch)
        transport.complete(success("after-intervening", 3))
        assertEquals("after-intervening", core.lastFetchedSnapshot()?.releaseUid)
    }

    @Test
    fun `304 without matching canonical body retries exactly once without ETag`() {
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        val results = mutableListOf<RemoteConfigFetchResult>()

        coordinator.fetch(callback = results::add)
        assertEquals(null, transport.requests.single().ifNoneMatch)
        transport.complete(RemoteConfigFetchResponse.NotModified(etag = "\"${"f".repeat(64)}\""))

        assertEquals(2, transport.requests.size)
        assertEquals(null, transport.requests.last().ifNoneMatch)
        assertTrue(results.isEmpty())
        transport.complete(RemoteConfigFetchResponse.NotModified())
        assertEquals(2, transport.requests.size)
        assertTrue(results.single() is RemoteConfigFetchResult.InvalidNotModified)
    }

    @Test
    fun `identity transition completes old waiters and fences the late response`() {
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        val oldResults = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = oldResults::add)

        val nextBinding = binding.copy(
            scope = RemoteConfigSnapshotScope("project", "production", "canonical-user-next"),
            expectation = binding.expectation.copy(contextFingerprint = "b".repeat(64)),
        )
        coordinator.transitionTo(nextBinding)
        assertEquals(listOf(RemoteConfigFetchResult.Superseded), oldResults)
        transport.complete(success("late-private", 1))
        assertEquals(null, core.lastFetchedSnapshot())

        val nextResults = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(forceReason = RemoteConfigFetchForceReason.Identify, callback = nextResults::add)
        transport.complete(success("wrong-context", 1))
        val transition = (nextResults.single() as RemoteConfigFetchResult.Fetched).transition
        assertEquals(RemoteConfigSnapshotTransitionStatus.Rejected, transition.status)
        assertEquals(null, core.lastFetchedSnapshot())
    }

    @Test
    fun `same visible binding can be explicitly generation fenced on identify`() {
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null)
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        val results = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = results::add)

        coordinator.transitionTo(binding)
        transport.complete(success("stale", 1))

        assertEquals(listOf(RemoteConfigFetchResult.Superseded), results)
        assertEquals(null, core.lastFetchedSnapshot())
    }

    @Test
    fun `identity transition waits for admitted response and its callback delivery boundary`() {
        val parserStarted = CountDownLatch(1)
        val releaseParser = CountDownLatch(1)
        val parser = RemoteConfigSnapshotEnvelopeDecoder { body, etag, expectation ->
            parserStarted.countDown()
            assertTrue(releaseParser.await(2, TimeUnit.SECONDS))
            RemoteConfigSnapshotEnvelopeParser().parse(body, etag, expectation)
        }
        val transport = RecordingTransport()
        val core = RemoteConfigSnapshotCore(
            store = InMemorySnapshotStore(),
            bundledRelease = null,
            envelopeParser = parser,
        )
        val coordinator = coordinator(transport = transport, core = core)
        coordinator.transitionTo(binding)
        val events = Collections.synchronizedList(mutableListOf<String>())
        coordinator.fetch { events += "callback" }

        val responseThread = Thread { transport.complete(success("admitted", 1)) }
        responseThread.start()
        assertTrue(parserStarted.await(2, TimeUnit.SECONDS))
        val transitionFinished = CountDownLatch(1)
        val transitionThread = Thread {
            coordinator.transitionTo(null)
            events += "transition"
            transitionFinished.countDown()
        }
        transitionThread.start()

        assertFalse(transitionFinished.await(100, TimeUnit.MILLISECONDS))
        releaseParser.countDown()
        responseThread.join(2_000)
        transitionThread.join(2_000)
        assertEquals(listOf("callback", "transition"), events)
    }

    @Test
    fun `one throwing coalesced callback cannot starve the remaining waiters`() {
        val transport = RecordingTransport()
        val coordinator = coordinator(transport)
        coordinator.transitionTo(binding)
        val delivered = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch { throw AssertionError("consumer failure") }
        coordinator.fetch(callback = delivered::add)

        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 400))

        assertTrue(delivered.single() is RemoteConfigFetchResult.Failed)
    }

    @Test
    fun `reentrant identity transition converts every remaining claimed callback to Superseded`() {
        val transport = RecordingTransport()
        val coordinator = coordinator(transport)
        coordinator.transitionTo(binding)
        val first = mutableListOf<RemoteConfigFetchResult>()
        val second = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch { result ->
            first += result
            coordinator.transitionTo(null)
        }
        coordinator.fetch(callback = second::add)

        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 400))

        assertTrue(first.single() is RemoteConfigFetchResult.Failed)
        assertEquals(listOf(RemoteConfigFetchResult.Superseded), second)
    }

    @Test
    fun `callback delivery holds no coordinator monitor needed by a concurrent transition`() {
        val transport = RecordingTransport()
        val coordinator = coordinator(transport)
        coordinator.transitionTo(binding)
        val transitionCompletedInsideCallback = AtomicBoolean(false)
        coordinator.fetch {
            val completed = CountDownLatch(1)
            Thread {
                coordinator.transitionTo(null)
                completed.countDown()
            }.start()
            transitionCompletedInsideCallback.set(completed.await(2, TimeUnit.SECONDS))
        }

        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 400))

        assertTrue(transitionCompletedInsideCallback.get())
    }

    @Test
    fun `failed durable backoff is observable conservative in process and explicit after restart`() {
        val transport = RecordingTransport()
        val clock = MutableClock(1_000)
        val policyStore = InMemoryFetchPolicyStore(saveSucceeds = false)
        val policy = RemoteConfigFetchPolicy(
            minimumFetchIntervalMillis = 0,
            initialBackoffMillis = 1_000,
            maximumBackoffMillis = 10_000,
        )
        val coordinator = coordinator(transport, clock, policyStore, policy)
        coordinator.transitionTo(binding)
        val failed = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = failed::add)
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 500))

        assertTrue(failed.single() is RemoteConfigFetchResult.PolicyPersistenceFailed)
        val guarded = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(forceReason = RemoteConfigFetchForceReason.Build, callback = guarded::add)
        assertTrue(guarded.single() is RemoteConfigFetchResult.Backoff)

        val restartedTransport = RecordingTransport()
        val restarted = coordinator(restartedTransport, clock, policyStore, policy)
        restarted.transitionTo(binding)
        restarted.fetch(callback = {})
        assertEquals(1, restartedTransport.requests.size)
    }

    @Test
    fun `transport and timeout scheduler failures still complete or continue the operation`() {
        val transportFailure = coordinator(RemoteConfigFetchTransport { _, _ -> error("transport") })
        transportFailure.transitionTo(binding)
        val failed = mutableListOf<RemoteConfigFetchResult>()
        transportFailure.fetch(callback = failed::add)
        assertTrue(failed.single() is RemoteConfigFetchResult.Failed)

        val transport = RecordingTransport()
        val schedulerFailure = coordinator(
            transport = transport,
            scheduler = RemoteConfigFetchScheduler { _, _ -> error("scheduler") },
            policy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 0, timeoutMillis = 100),
        )
        schedulerFailure.transitionTo(binding)
        val recovered = mutableListOf<RemoteConfigFetchResult>()
        schedulerFailure.fetch(callback = recovered::add)
        transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 400))
        assertTrue(recovered.single() is RemoteConfigFetchResult.Failed)
    }

    @Test
    fun `unbounded restored deadline is clamped to max and sanitized durably`() {
        val transport = RecordingTransport()
        val policyStore = InMemoryFetchPolicyStore().apply {
            save(
                RemoteConfigFetchPolicyScope.from(scope),
                RemoteConfigFetchPolicyState(
                    consecutiveRetryableFailures = 63,
                    nextAllowedFetchAtMillis = Long.MAX_VALUE,
                ),
            )
        }
        val coordinator = coordinator(
            transport = transport,
            policyStore = policyStore,
            policy = RemoteConfigFetchPolicy(
                minimumFetchIntervalMillis = 0,
                initialBackoffMillis = 1_000,
                maximumBackoffMillis = 10_000,
            ),
        )
        coordinator.transitionTo(binding)
        val result = mutableListOf<RemoteConfigFetchResult>()
        coordinator.fetch(callback = result::add)

        assertEquals(0, transport.requests.size)
        assertEquals(11_000L, (result.single() as RemoteConfigFetchResult.Backoff).nextAllowedAtMillis)
        assertEquals(
            11_000L,
            policyStore.load(RemoteConfigFetchPolicyScope.from(scope))?.nextAllowedFetchAtMillis,
        )
    }

    @Test
    fun `invalid random sources always produce conservative nonzero jitter`() {
        val randoms = listOf(
            RemoteConfigFetchRandom { throw IllegalStateException("rng") },
            RemoteConfigFetchRandom { Double.NaN },
            RemoteConfigFetchRandom { -1.0 },
            RemoteConfigFetchRandom { 1.0 },
        )
        randoms.forEach { invalidRandom ->
            val transport = RecordingTransport()
            val policyStore = InMemoryFetchPolicyStore()
            val coordinator = coordinator(
                transport = transport,
                random = invalidRandom,
                policyStore = policyStore,
                policy = RemoteConfigFetchPolicy(
                    minimumFetchIntervalMillis = 0,
                    initialBackoffMillis = 1_000,
                    maximumBackoffMillis = 10_000,
                ),
            )
            coordinator.transitionTo(binding)
            coordinator.fetch(callback = {})
            transport.complete(RemoteConfigFetchResponse.Failure(statusCode = 500))
            assertTrue(
                requireNotNull(
                    policyStore.load(RemoteConfigFetchPolicyScope.from(scope)),
                ).nextAllowedFetchAtMillis > 1_000,
            )
        }
    }

    private fun coordinator(
        transport: RemoteConfigFetchTransport,
        clock: MutableClock = MutableClock(1_000),
        policyStore: InMemoryFetchPolicyStore = InMemoryFetchPolicyStore(),
        policy: RemoteConfigFetchPolicy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 0),
        random: RemoteConfigFetchRandom = MutableRandom(0.5),
        core: RemoteConfigSnapshotCore = RemoteConfigSnapshotCore(InMemorySnapshotStore(), bundledRelease = null),
        scheduler: RemoteConfigFetchScheduler = RemoteConfigFetchScheduler { _, _ ->
            RemoteConfigFetchScheduledTask {}
        },
    ): RemoteConfigFetchCoordinator {
        return RemoteConfigFetchCoordinator(
            core = core,
            transport = transport,
            policyStore = policyStore,
            clock = clock,
            random = random,
            scheduler = scheduler,
            policy = policy,
        )
    }

    private fun success(uid: String, number: Long): RemoteConfigFetchResponse.Success {
        val body = wireBody(uid, number).encodeToByteArray()
        return RemoteConfigFetchResponse.Success(body, strongETag(body))
    }

    private fun wireBody(uid: String, number: Long) =
        "{\"schema_version\":1,\"project_id\":42,\"environment_uid\":\"production\"," +
            "\"release_uid\":\"$uid\",\"release_number\":$number," +
            "\"manifest_content_hash\":\"${number.toString(16).padStart(64, '0')}\"," +
            "\"complete_key_set\":true,\"context_fingerprint\":\"${"a".repeat(64)}\"," +
            "\"values\":{\"a\":{\"raw\":$number,\"variation_uid\":\"variation-$uid\"," +
            "\"apply_policy\":\"on_next_activate\",\"metadata\":null}}}"

    private fun strongETag(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(body)
        .joinToString(prefix = "\"", postfix = "\"", separator = "") { byte -> "%02x".format(byte) }

    private fun release(uid: String, number: Long, value: String) = RemoteConfigSnapshotRelease(
        releaseUid = uid,
        releaseNumber = number,
        manifestContentHash = number.toString(16).padStart(64, '0'),
        entries = listOf(
            RemoteConfigSnapshotEntry.value(
                key = "a",
                rawValue = value.encodeToByteArray(),
                variationUid = "variation-$uid",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = null,
            ),
        ),
    )

    private class MutableClock(var now: Long) : RemoteConfigFetchClock {
        override fun nowMillis(): Long = now
    }

    private class MutableRandom(var value: Double) : RemoteConfigFetchRandom {
        override fun nextDouble(): Double = value
    }

    private class ManualScheduler : RemoteConfigFetchScheduler {
        private val tasks = mutableListOf<Task>()

        override fun schedule(delayMillis: Long, action: () -> Unit): RemoteConfigFetchScheduledTask {
            val task = Task(action)
            tasks += task
            return RemoteConfigFetchScheduledTask { task.cancelled = true }
        }

        fun runNext() {
            val task = tasks.removeAt(0)
            if (!task.cancelled) task.action()
        }

        private data class Task(val action: () -> Unit, var cancelled: Boolean = false)
    }

    private class RecordingTransport : RemoteConfigFetchTransport {
        val requests = mutableListOf<RemoteConfigFetchRequest>()
        private val completions = mutableListOf<(RemoteConfigFetchResponse) -> Unit>()

        override fun fetch(
            request: RemoteConfigFetchRequest,
            completion: (RemoteConfigFetchResponse) -> Unit,
        ) {
            requests += request
            completions += completion
        }

        fun complete(response: RemoteConfigFetchResponse) = completions.removeAt(0)(response)
    }

    private class InMemoryFetchPolicyStore(
        private val saveSucceeds: Boolean = true,
    ) : RemoteConfigFetchPolicyStore {
        private val states = mutableMapOf<RemoteConfigFetchPolicyScope, RemoteConfigFetchPolicyState>()

        override fun load(scope: RemoteConfigFetchPolicyScope): RemoteConfigFetchPolicyState? = states[scope]

        override fun save(scope: RemoteConfigFetchPolicyScope, state: RemoteConfigFetchPolicyState): Boolean {
            if (saveSucceeds) states[scope] = state
            return saveSucceeds
        }
    }

    private class InMemorySnapshotStore : RemoteConfigSnapshotStore {
        private val states = mutableMapOf<RemoteConfigSnapshotScope, RemoteConfigSnapshotState>()

        override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult =
            states[scope]?.let { RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Found, it) }
                ?: RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Missing)

        override fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean {
            states[scope] = state
            return true
        }
    }
}
