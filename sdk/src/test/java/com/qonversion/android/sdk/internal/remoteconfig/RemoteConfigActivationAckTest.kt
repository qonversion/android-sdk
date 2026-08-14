package com.qonversion.android.sdk.internal.remoteconfig

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * The activation ack, end to end over a real [MockWebServer]: the shipped
 * [RemoteConfigGatewayTransport] under the shipped [RemoteConfigActivationAckSender].
 *
 * Only three things are doubles: the durable store is in memory, the retry scheduler is manual (so
 * a bounded retry ladder can be walked without sleeping), and the jitter source is fixed.
 */
internal class RemoteConfigActivationAckTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var gateway: ScriptedGateway
    private lateinit var store: InMemoryActivationAckStore
    private lateinit var sessionStore: InMemorySessionStore
    private lateinit var scheduler: ManualScheduler
    private var identityScope: RemoteConfigSnapshotScope? = SCOPE_A
    private val clock = MutableAckClock(ACTIVATED_AT_SECONDS * 1_000)

    @Before
    fun setUp() {
        server = MockWebServer()
        gateway = ScriptedGateway()
        server.dispatcher = gateway
        server.start()
        client = OkHttpClient()
        store = InMemoryActivationAckStore()
        scheduler = ManualScheduler()
        identityScope = SCOPE_A
        // An ack can only ever follow a snapshot read, so the realistic starting state is a session
        // this installation already holds — which is also the state that licenses the single
        // re-bootstrap on a 401.
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
        server.shutdown()
        client.dispatcher().executorService().shutdownNow()
        client.connectionPool().evictAll()
    }

    @Test
    fun `an ack matches the gateway contract exactly`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(1)

        val ack = gateway.acks.single()
        assertEquals("POST", ack.method)
        assertEquals(RC_ACK_PATH, ack.path)
        assertEquals("Bearer $PROJECT_TOKEN", ack.authorization)
        assertEquals("application/json; charset=utf-8", ack.contentType)
        assertEquals(SEEDED_SESSION_TOKEN, ack.sessionHeader)
        assertEquals("{\"release_number\":$RELEASE_7,\"activated_at\":$ACTIVATED_AT_SECONDS}", ack.body)
        awaitRecord { it?.pending == null && it?.settledReleaseNumber == RELEASE_7 }
        assertEquals(0, sender.droppedAckCount)
    }

    @Test
    fun `a delivered release is never acked twice`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(1)
        awaitRecord { it?.settledReleaseNumber == RELEASE_7 }

        // The same release re-reported (an implicit activation followed by an explicit activate()).
        sender.recordActivation(SCOPE_A, RELEASE_7)
        // ...and re-reported after a rebind, which is what a second cold start looks like.
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)

        assertEquals(1, gateway.acks.size)
    }

    @Test
    fun `a 401 re-bootstraps exactly once and retries the ack`() {
        gateway.scriptAck(HTTP_UNAUTHORIZED)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(2)

        awaitRecord { it?.settledReleaseNumber == RELEASE_7 && it.pending == null }
        // Exactly one mint: the single re-bootstrap the 401 licensed, and no other.
        assertEquals(1, gateway.sessions.size)
        assertEquals(listOf(SEEDED_SESSION_TOKEN, SESSION_TOKEN), gateway.acks.map { it.sessionHeader })
        assertEquals(0, sender.droppedAckCount)
        // No retry was ever scheduled: the re-bootstrap is the transport's business, not the
        // sender's retry budget.
        assertEquals(emptyList<Long>(), scheduler.requestedDelays)
    }

    @Test
    fun `a 401 that survives the re-bootstrap is permanent`() {
        gateway.scriptAck(HTTP_UNAUTHORIZED, HTTP_UNAUTHORIZED)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(2)

        awaitDropped(sender, 1)
        // Permanent is an answer: the release is settled, not left owed.
        awaitRecord { it?.pending == null && it?.settledReleaseNumber == RELEASE_7 }
        scheduler.runAll()
        assertEquals(2, gateway.acks.size)
    }

    @Test
    fun `a permanent refusal settles the release instead of forgetting it`() {
        // A gateway that does not serve /ack at all answers every ack with 404. Forgetting such a
        // release would re-queue and re-POST the same ack on every start and every binding.
        gateway.scriptAck(HTTP_NOT_FOUND)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(1)
        awaitDropped(sender, 1)
        awaitRecord { it?.pending == null && it?.settledReleaseNumber == RELEASE_7 }

        scheduler.runAll()
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        // A whole new process over the same durable state must stay silent as well.
        sender().bind(SCOPE_A)

        assertEquals(1, gateway.acks.size)
    }

    @Test
    fun `a 503 is retried to the attempt bound and then dropped`() {
        gateway.scriptAck(HTTP_SERVICE_UNAVAILABLE, HTTP_SERVICE_UNAVAILABLE, HTTP_SERVICE_UNAVAILABLE)
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordActivation(SCOPE_A, RELEASE_7)
        runRetryLadder(sender)

        // Three attempts, half-cap plus jitter, and nothing scheduled afterwards.
        assertEquals(REMOTE_CONFIG_ACK_MAX_ATTEMPTS, gateway.acks.size)
        assertEquals(listOf(750L, 1_500L), scheduler.requestedDelays)
        assertEquals(0, scheduler.pendingCount())
        scheduler.runAll()
        assertEquals(REMOTE_CONFIG_ACK_MAX_ATTEMPTS, gateway.acks.size)
        // Dropped in this process, still owed: the record is what a later start picks up.
        assertEquals(RELEASE_7, store.load(SCOPE_A)?.pending?.releaseNumber)
    }

    @Test
    fun `an exhausted retry ladder is not re-armed by a re-binding`() {
        gateway.scriptAck(*IntArray(RETRY_SCRIPT_SIZE) { HTTP_SERVICE_UNAVAILABLE })
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        runRetryLadder(sender)

        // An identify that lands on the same identity, and a re-report of the same activation.
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        sender.bind(SCOPE_B)
        sender.bind(SCOPE_A)

        assertEquals(REMOTE_CONFIG_ACK_MAX_ATTEMPTS, gateway.acks.size)
        // Only a NEWER release re-arms delivery.
        sender.recordActivation(SCOPE_A, RELEASE_9)
        awaitAcks(REMOTE_CONFIG_ACK_MAX_ATTEMPTS + 1)
        assertEquals(RELEASE_9, gateway.acks.last().releaseNumber())
    }

    @Test
    fun `a newer activation supersedes the queued one`() {
        gateway.scriptAck(HTTP_SERVICE_UNAVAILABLE)
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(1)

        clock.now = LATER_ACTIVATED_AT_SECONDS * 1_000
        sender.recordActivation(SCOPE_A, RELEASE_9)
        awaitAcks(2)

        assertEquals(
            "{\"release_number\":$RELEASE_9,\"activated_at\":$LATER_ACTIVATED_AT_SECONDS}",
            gateway.acks[1].body,
        )
        awaitRecord { it?.settledReleaseNumber == RELEASE_9 && it.pending == null }
        // The superseded retry never fires, so release 7 is never re-sent.
        scheduler.runAll()
        assertEquals(2, gateway.acks.size)
        assertEquals(listOf(RELEASE_7, RELEASE_9), gateway.acks.map { it.releaseNumber() })
    }

    @Test
    fun `a pending ack survives a process restart`() {
        gateway.scriptAck(HTTP_SERVICE_UNAVAILABLE, HTTP_SERVICE_UNAVAILABLE, HTTP_SERVICE_UNAVAILABLE)
        val crashed = sender()
        crashed.bind(SCOPE_A)
        crashed.recordActivation(SCOPE_A, RELEASE_7)
        runRetryLadder(crashed)

        // A new process: new sender, new scheduler, same durable store.
        scheduler = ManualScheduler()
        clock.now = LATER_ACTIVATED_AT_SECONDS * 1_000
        val restarted = sender()
        restarted.bind(SCOPE_A)
        awaitAcks(4)

        // The ack still reports when the release was ACTIVATED, not when it was finally delivered.
        assertEquals(
            "{\"release_number\":$RELEASE_7,\"activated_at\":$ACTIVATED_AT_SECONDS}",
            gateway.acks[3].body,
        )
        awaitRecord { it != null && it.pending == null && it.settledReleaseNumber == RELEASE_7 }
        assertEquals(0, restarted.droppedAckCount)
    }

    @Test
    fun `re-binding the same identity does not re-send an ack already under way`() {
        gateway.scriptAck(HTTP_SERVICE_UNAVAILABLE)
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(1)

        // An identify that resolves to the identity already bound.
        sender.bind(SCOPE_A)

        assertEquals(1, gateway.acks.size)
        // ...and the retry the 503 armed is still the one that will run.
        assertEquals(1, scheduler.pendingCount())
        scheduler.runAll()
        awaitAcks(2)
        awaitRecord { it?.settledReleaseNumber == RELEASE_7 }
        assertEquals(2, gateway.acks.size)
    }

    @Test
    fun `an ack is never sent under another identity's session`() {
        val sender = sender()
        sender.bind(SCOPE_A)
        // The transport now addresses another identity than the one the ack was queued for.
        identityScope = SCOPE_B

        sender.recordActivation(SCOPE_A, RELEASE_7)

        assertEquals(emptyList<RecordedAck>(), gateway.acks)
        assertEquals(0, sender.droppedAckCount)
        // Not an attempt: the ack stays queued for the identity that owes it.
        assertEquals(RELEASE_7, store.load(SCOPE_A)?.pending?.releaseNumber)
    }

    @Test
    fun `an activation of an unbound scope is ignored`() {
        val sender = sender()

        sender.recordActivation(SCOPE_A, RELEASE_7)
        sender.bind(SCOPE_B)
        sender.recordActivation(SCOPE_A, RELEASE_7)

        assertEquals(emptyList<RecordedAck>(), gateway.acks)
        assertNull(store.load(SCOPE_A))
    }

    @Test
    fun `binding another identity fences an ack that is already on the wire`() {
        gateway.scriptAck(HTTP_SERVICE_UNAVAILABLE)
        val sender = sender()
        sender.bind(SCOPE_A)
        sender.recordActivation(SCOPE_A, RELEASE_7)
        awaitAcks(1)

        identityScope = SCOPE_B
        sender.bind(SCOPE_B)
        scheduler.runAll()

        // The retry the 503 scheduled belongs to the previous identity and must not fire.
        assertEquals(1, gateway.acks.size)
        assertEquals(RELEASE_7, store.load(SCOPE_A)?.pending?.releaseNumber)
    }

    @Test
    fun `a release number that could never address anything is refused`() {
        val sender = sender()
        sender.bind(SCOPE_A)

        sender.recordActivation(SCOPE_A, 0)
        sender.recordActivation(SCOPE_A, -1)

        assertEquals(emptyList<RecordedAck>(), gateway.acks)
        assertNull(store.load(SCOPE_A))
    }

    private fun sender() = RemoteConfigActivationAckSender(
        transport = transport(),
        store = store,
        clock = clock,
        random = { 0.5 },
        scheduler = scheduler,
    )

    private fun transport() = RemoteConfigGatewayTransport(
        callFactory = client,
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

    /** Walks the bounded retry ladder to its end, without racing the timer it arms. */
    private fun runRetryLadder(sender: RemoteConfigActivationAckSender) {
        repeat(REMOTE_CONFIG_ACK_MAX_ATTEMPTS - 1) { attempt ->
            awaitAcks(attempt + 1)
            await("no retry was scheduled after attempt ${attempt + 1}") { scheduler.pendingCount() > 0 }
            scheduler.runAll()
        }
        awaitAcks(REMOTE_CONFIG_ACK_MAX_ATTEMPTS)
        awaitDropped(sender, 1)
    }

    private fun awaitAcks(count: Int) = await("expected $count acks, saw ${gateway.acks.size}") {
        gateway.acks.size >= count
    }

    private fun awaitDropped(sender: RemoteConfigActivationAckSender, count: Long) =
        await("expected $count dropped acks, saw ${sender.droppedAckCount}") {
            sender.droppedAckCount >= count
        }

    private fun awaitRecord(predicate: (RemoteConfigActivationAckRecord?) -> Boolean) =
        await("durable ack record never reached the expected shape: ${store.load(SCOPE_A)}") {
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

    private fun RecordedAck.releaseNumber(): Long =
        RELEASE_NUMBER_PATTERN.find(body)?.groupValues?.get(1)?.toLong() ?: 0

    private class MutableAckClock(@Volatile var now: Long) : RemoteConfigFetchClock {
        override fun nowMillis(): Long = now
    }

    private data class RecordedAck(
        val method: String,
        val path: String?,
        val body: String,
        val sessionHeader: String?,
        val authorization: String?,
        val contentType: String?,
    )

    /** Answers by path, so an ack and a bootstrap are never order-coupled. */
    private class ScriptedGateway : Dispatcher() {
        val acks: MutableList<RecordedAck> = Collections.synchronizedList(mutableListOf())
        val sessions: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val ackStatuses = ArrayDeque<Int>()

        /** Answers the next acks with [statuses], then `204` forever. */
        fun scriptAck(vararg statuses: Int) {
            synchronized(ackStatuses) { statuses.forEach(ackStatuses::addLast) }
        }

        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            RC_SESSION_PATH -> {
                sessions += request.body.readUtf8()
                MockResponse().setResponseCode(HTTP_OK).setBody(sessionBody(sessions.size))
            }
            RC_ACK_PATH -> {
                acks += RecordedAck(
                    method = request.method.orEmpty(),
                    path = request.path,
                    body = request.body.readUtf8(),
                    sessionHeader = request.getHeader(REMOTE_CONFIG_SESSION_HEADER),
                    authorization = request.getHeader("Authorization"),
                    contentType = request.getHeader("Content-Type"),
                )
                MockResponse().setResponseCode(
                    synchronized(ackStatuses) { ackStatuses.pollFirst() } ?: HTTP_NO_CONTENT,
                )
            }
            else -> MockResponse().setResponseCode(HTTP_NOT_FOUND)
        }

        private fun sessionBody(ordinal: Int): String {
            val token = if (ordinal == 1) SESSION_TOKEN else "$SESSION_TOKEN-$ordinal"
            return "{\"session_token\":\"$token\",\"project_id\":$RC_PROJECT_ID," +
                "\"environment\":\"prod\",\"expires_at\":\"2030-01-01T00:00:00Z\"}"
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 10L
        const val PROJECT_TOKEN = "project-token"
        const val SESSION_TOKEN = "qrcs1.session"
        const val SEEDED_SESSION_TOKEN = "qrcs1.seeded-session"
        const val SESSION_LIFETIME_MILLIS = 3_600_000L
        const val RELEASE_7 = 7L
        const val RELEASE_9 = 9L
        const val ACTIVATED_AT_SECONDS = 1_700_000_000L
        const val LATER_ACTIVATED_AT_SECONDS = 1_700_000_900L
        const val RETRY_SCRIPT_SIZE = 8
        val RELEASE_NUMBER_PATTERN = Regex("\"release_number\":(\\d+)")
        val SCOPE_A = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "QON_anon_a")
        val SCOPE_B = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "QON_anon_b")
    }
}
