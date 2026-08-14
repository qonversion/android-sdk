package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end proof that [RemoteConfigGatewayTransport] plugs into the existing fetch-policy engine:
 * real [RemoteConfigFetchCoordinator], real [RemoteConfigSnapshotCore] with the strict wire parser,
 * real HTTP over [MockWebServer]. Nothing between the socket and durable admission is stubbed.
 */
internal class RemoteConfigGatewayTransportCoordinatorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val snapshotStore = InMemorySnapshotStore()
    private val policyStore = InMemoryFetchPolicyStore()
    private val scheduler = ManualScheduler()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `server bytes reach durable admission unchanged`() {
        val core = core()
        val coordinator = coordinator(core)
        coordinator.transitionTo(SCOPE)
        val body = WIRE_BODY.toByteArray(Charsets.UTF_8)
        server.enqueue(sessionResponse())
        server.enqueue(snapshotResponse(body, strongETag(body)))

        val result = fetch(coordinator)

        val fetched = result as RemoteConfigFetchResult.Fetched
        assertTrue(
            fetched.transition.status.name,
            fetched.transition.status == RemoteConfigSnapshotTransitionStatus.Accepted ||
                fetched.transition.status == RemoteConfigSnapshotTransitionStatus.Activated,
        )
        val admitted = requireNotNull(snapshotStore.states[SCOPE]?.candidate)
        assertArrayEquals(body, admitted.canonicalBodyBytes)
        assertEquals(strongETag(body), admitted.strongETag)
    }

    @Test
    fun `304 is recovered against the current head instead of re-admitting`() {
        val core = core()
        val coordinator = coordinator(core)
        coordinator.transitionTo(SCOPE)
        val body = WIRE_BODY.toByteArray(Charsets.UTF_8)
        server.enqueue(sessionResponse())
        server.enqueue(snapshotResponse(body, strongETag(body)))
        assertTrue(fetch(coordinator) is RemoteConfigFetchResult.Fetched)
        server.takeRequest()
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", strongETag(body)))
        val result = fetch(coordinator)

        assertEquals(RemoteConfigFetchResult.NotModified, result)
        // The session was reused, so the only new request is the conditional snapshot read.
        val conditional = server.takeRequest()
        assertEquals("/v3/remote-config-v2/snapshot", conditional.path)
        assertEquals(strongETag(body), conditional.getHeader("If-None-Match"))
    }

    @Test
    fun `the bootstrapped project id is what a snapshot is admitted against`() {
        // Nothing in the app configured 43: the session bootstrap alone establishes the project the
        // snapshot must belong to, and this envelope names 42.
        val core = core()
        val coordinator = coordinator(core)
        coordinator.transitionTo(SCOPE)
        val body = WIRE_BODY.toByteArray(Charsets.UTF_8)
        server.enqueue(sessionResponse(projectId = 43))
        server.enqueue(snapshotResponse(body, strongETag(body)))

        val result = fetch(coordinator)

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Rejected,
            (result as RemoteConfigFetchResult.Fetched).transition.status,
        )
        assertNull(snapshotStore.states[SCOPE]?.candidate)
    }

    @Test
    fun `a later bootstrap that changes the project id is a typed failure, not a re-learn`() {
        val core = core()
        val coordinator = coordinator(core)
        coordinator.transitionTo(SCOPE)
        val body = WIRE_BODY.toByteArray(Charsets.UTF_8)
        server.enqueue(sessionResponse())
        server.enqueue(snapshotResponse(body, strongETag(body)))
        assertTrue(fetch(coordinator) is RemoteConfigFetchResult.Fetched)

        // A 401 drops the established session, so the next read re-bootstraps — and this time the
        // gateway answers for a different project.
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(sessionResponse(projectId = 43))

        assertEquals(RemoteConfigFetchResult.ProjectMismatch, fetch(coordinator))
        // Snapshot, session, snapshot, session: no read was attempted on the refused session.
        assertEquals(4, server.requestCount)

        // And the refusal arms the failure backoff, so an identify/logout loop cannot turn a
        // misrouted gateway into one bootstrap round trip per call. Forced fetches bypass the
        // minimum interval, never this gate.
        val gated = fetch(coordinator, RemoteConfigFetchForceReason.Identify)
        assertTrue(gated.toString(), gated is RemoteConfigFetchResult.Backoff)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `a stalled gateway times out through the fetch policy`() {
        val core = core()
        val coordinator = coordinator(core)
        coordinator.transitionTo(SCOPE)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val latch = CountDownLatch(1)
        var result: RemoteConfigFetchResult? = null
        coordinator.fetch { fetchResult ->
            result = fetchResult
            latch.countDown()
        }
        scheduler.runNext()

        assertTrue(latch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(result is RemoteConfigFetchResult.TimedOut)
        // The HTTP call is deliberately not cancelled on timeout: the coordinator fences the late
        // response with a fresh admission token instead, so the request still reaches the server.
        assertNotNull(server.takeRequest(AWAIT_SECONDS, TimeUnit.SECONDS))
    }

    private fun fetch(
        coordinator: RemoteConfigFetchCoordinator,
        forceReason: RemoteConfigFetchForceReason? = null,
    ): RemoteConfigFetchResult {
        val latch = CountDownLatch(1)
        var result: RemoteConfigFetchResult? = null
        coordinator.fetch(forceReason) { fetchResult ->
            result = fetchResult
            latch.countDown()
        }
        assertTrue("coordinator did not answer in time", latch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun core() = RemoteConfigSnapshotCore(snapshotStore, bundledRelease = null)

    private fun coordinator(core: RemoteConfigSnapshotCore) = RemoteConfigFetchCoordinator(
        core = core,
        transport = transport(),
        policyStore = policyStore,
        clock = { CLOCK_MILLIS },
        random = { 0.5 },
        scheduler = scheduler,
        policy = RemoteConfigFetchPolicy(minimumFetchIntervalMillis = 0, timeoutMillis = 5_000),
    )

    private fun transport() = RemoteConfigGatewayTransport(
        callFactory = client,
        baseUrlProvider = { server.url("/").toString() },
        identityProvider = {
            RemoteConfigTransportIdentity(SCOPE, "project-key-secret", "QON_anon_a")
        },
        clientContextProvider = {
            RemoteConfigClientContext(
                platform = "android",
                appVersion = "1.2.3",
                osVersion = "14",
                sdkVersion = "9.7.0",
                locale = "en_US",
                deviceModel = "Pixel 8",
                deviceInstalledAtSeconds = 1_577_836_800,
            )
        },
        sessionStore = InMemorySessionStore(),
        projectIds = RemoteConfigProjectIdRegistry(InMemoryProjectIdStore()),
        clock = { CLOCK_MILLIS },
        moshi = Moshi.Builder().build(),
        logger = SilentLogger(),
    )

    private fun sessionResponse(projectId: Long = 42) = MockResponse()
        .setResponseCode(200)
        .setBody(
            "{\"session_token\":\"qrcs1.session-secret\",\"project_id\":$projectId," +
                "\"environment\":\"prod\",\"expires_at\":\"2030-01-01T00:00:00Z\"}",
        )

    private fun snapshotResponse(body: ByteArray, etag: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("ETag", etag)
        .setBody(Buffer().write(body))

    private class ManualScheduler : RemoteConfigFetchScheduler {
        private val tasks = mutableListOf<Task>()

        override fun schedule(delayMillis: Long, action: () -> Unit): RemoteConfigFetchScheduledTask {
            val task = Task(action)
            synchronized(tasks) { tasks += task }
            return RemoteConfigFetchScheduledTask { task.cancelled = true }
        }

        fun runNext() {
            val task = synchronized(tasks) { tasks.removeAt(0) }
            if (!task.cancelled) task.action()
        }

        private class Task(val action: () -> Unit, @Volatile var cancelled: Boolean = false)
    }

    private class InMemorySessionStore : RemoteConfigSessionStore {
        private val sessions = mutableMapOf<RemoteConfigSessionKey, RemoteConfigGatewaySession>()

        @Synchronized
        override fun load(key: RemoteConfigSessionKey) = sessions[key]

        @Synchronized
        override fun save(key: RemoteConfigSessionKey, session: RemoteConfigGatewaySession): Boolean {
            sessions[key] = session
            return true
        }

        @Synchronized
        override fun clear(key: RemoteConfigSessionKey): Boolean {
            sessions.remove(key)
            return true
        }
    }

    private class InMemoryFetchPolicyStore : RemoteConfigFetchPolicyStore {
        private val states = mutableMapOf<RemoteConfigFetchPolicyScope, RemoteConfigFetchPolicyState>()

        @Synchronized
        override fun load(scope: RemoteConfigFetchPolicyScope) = states[scope]

        @Synchronized
        override fun save(scope: RemoteConfigFetchPolicyScope, state: RemoteConfigFetchPolicyState): Boolean {
            states[scope] = state
            return true
        }
    }

    private class InMemorySnapshotStore : RemoteConfigSnapshotStore {
        val states = mutableMapOf<RemoteConfigSnapshotScope, RemoteConfigSnapshotState>()

        @Synchronized
        override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult =
            states[scope]?.let { RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Found, it) }
                ?: RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Missing)

        @Synchronized
        override fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean {
            states[scope] = state
            return true
        }
    }

    private class SilentLogger : Logger {
        override fun error(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun release(message: String) = Unit
        override fun debug(message: String) = Unit
    }

    private companion object {
        const val AWAIT_SECONDS = 10L
        const val CLOCK_MILLIS = 1_000_000L
        val SCOPE = RemoteConfigSnapshotScope("project", "production", "canonical-user")
        val WIRE_BODY = "{\"schema_version\":1,\"project_id\":42,\"environment_uid\":\"production\"," +
            "\"release_uid\":\"release-1\",\"release_number\":1," +
            "\"manifest_content_hash\":\"${"1".padStart(64, '0')}\"," +
            "\"complete_key_set\":true,\"context_fingerprint\":\"${"a".repeat(64)}\"," +
            "\"values\":{\"a\":{\"raw\":1,\"variation_uid\":\"variation-release-1\"," +
            "\"apply_policy\":\"on_next_activate\",\"metadata\":null}}}"

        fun strongETag(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(body)
            .joinToString(prefix = "\"", postfix = "\"", separator = "") { byte -> "%02x".format(byte) }
    }
}
