package com.qonversion.android.sdk.internal.remoteconfig

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val PROJECT_TOKEN = "project-token"
private const val SESSION_TOKEN = "qrcs1.session"
private const val SEEDED_SESSION_TOKEN = "qrcs1.seeded-session"
private const val SESSION_LIFETIME_MILLIS = 3_600_000L
private const val NOW_MILLIS = 1_700_000_000_000L
private const val OCCURRED_AT_SECONDS = 1_700_000_000L
private const val RELEASE_7 = 7L
private const val HTTP_BAD_REQUEST = 400

/**
 * The `/v3/remote-config-v2/telemetry` route of [RemoteConfigGatewayTransport], over a real
 * [MockWebServer].
 *
 * These tests are about the ROUTE, not the queue: what the transport puts on the wire, how it
 * classifies an answer, and — the rule that has teeth — what a `401` may and may not do to the
 * session the config read path depends on.
 */
internal class RemoteConfigTelemetryTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var gateway: ScriptedGateway
    private lateinit var sessionStore: InMemorySessionStore
    private var identityScope: RemoteConfigSnapshotScope? = SCOPE_A

    @Before
    fun setUp() {
        server = MockWebServer()
        gateway = ScriptedGateway()
        server.dispatcher = gateway
        server.start()
        client = OkHttpClient()
        identityScope = SCOPE_A
        sessionStore = InMemorySessionStore()
        sessionStore.save(
            RemoteConfigSessionKey(SCOPE_A, SCOPE_A.canonicalUserId),
            RemoteConfigGatewaySession(
                token = SEEDED_SESSION_TOKEN,
                projectId = RC_PROJECT_ID,
                environment = "prod",
                expiresAtMillis = NOW_MILLIS + SESSION_LIFETIME_MILLIS,
            ),
        )
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (_: Exception) {
            // One test shuts the gateway down itself to produce a transport fault.
        }
        client.dispatcher().executorService().shutdownNow()
        client.connectionPool().evictAll()
    }

    @Test
    fun `a batch is posted to the telemetry path with the session header`() {
        val response = post(listOf(decodeFailure("paywall_prices"), guardEvent()))

        assertEquals(RemoteConfigAckResponse.Delivered, response)
        val recorded = gateway.batches.single()
        assertEquals("POST", recorded.method)
        assertEquals(RC_TELEMETRY_PATH, recorded.path)
        assertEquals("Bearer $PROJECT_TOKEN", recorded.authorization)
        assertEquals(SEEDED_SESSION_TOKEN, recorded.sessionHeader)
        assertEquals("no-store", recorded.cacheControl)
        // Exactly the contract shape: logical_key present iff the kind is decode_failure.
        assertEquals(
            "{\"events\":[" +
                "{\"kind\":\"decode_failure\",\"logical_key\":\"paywall_prices\"," +
                "\"release_number\":$RELEASE_7,\"count\":1,\"last_occurred_at\":$OCCURRED_AT_SECONDS}," +
                "{\"kind\":\"read_before_activate\",\"release_number\":0," +
                "\"count\":2,\"last_occurred_at\":$OCCURRED_AT_SECONDS}" +
                "]}",
            recorded.body,
        )
    }

    @Test
    fun `a missing session is never bootstrapped by the telemetry route`() {
        sessionStore.clear(RemoteConfigSessionKey(SCOPE_A, SCOPE_A.canonicalUserId))

        val response = post(listOf(decodeFailure("paywall_prices")))

        // Session establishment belongs to the config read path. A diagnostic signal must not be
        // the reason an installation contacts the gateway, so the batch is refused as
        // NotAddressable — which costs the sender no retry budget and keeps the events buffered.
        assertEquals(RemoteConfigAckResponse.NotAddressable, response)
        assertTrue(gateway.sessions.isEmpty())
        assertTrue(gateway.batches.isEmpty())
    }

    @Test
    fun `a 401 re-bootstraps once and never forgets the shared session`() {
        gateway.script(HTTP_UNAUTHORIZED)

        val response = post(listOf(decodeFailure("paywall_prices")))

        assertEquals(RemoteConfigAckResponse.Delivered, response)
        assertEquals(1, gateway.sessions.size)
        assertEquals(listOf(SEEDED_SESSION_TOKEN, SESSION_TOKEN), gateway.batches.map { it.sessionHeader })
        // The stored session belongs to the config read path: telemetry replaces it by minting, it
        // never clears it.
        val stored = sessionStore.load(RemoteConfigSessionKey(SCOPE_A, SCOPE_A.canonicalUserId))
        assertNotNull("the telemetry route forgot the config read path's session", stored)
        assertEquals(SESSION_TOKEN, stored?.token)
    }

    @Test
    fun `a second 401 is permanent and still leaves the session alone`() {
        gateway.script(HTTP_UNAUTHORIZED, HTTP_UNAUTHORIZED)

        val response = post(listOf(decodeFailure("paywall_prices")))

        assertEquals(RemoteConfigAckResponse.Permanent, response)
        assertEquals(2, gateway.batches.size)
        assertNotNull(sessionStore.load(RemoteConfigSessionKey(SCOPE_A, SCOPE_A.canonicalUserId)))
    }

    @Test
    fun `statuses are classified exactly like the ack route`() {
        assertEquals(RemoteConfigAckResponse.Permanent, postWith(HTTP_BAD_REQUEST))
        assertEquals(RemoteConfigAckResponse.Permanent, postWith(HTTP_NOT_FOUND))
        assertEquals(RemoteConfigAckResponse.Retryable, postWith(HTTP_SERVICE_UNAVAILABLE))
        assertEquals(RemoteConfigAckResponse.Retryable, postWith(HTTP_SERVER_ERROR))
        assertEquals(RemoteConfigAckResponse.Delivered, postWith(HTTP_NO_CONTENT))
    }

    @Test
    fun `a batch is never posted under another identity's session`() {
        identityScope = SCOPE_B

        val response = post(listOf(decodeFailure("paywall_prices")))

        // Not an attempt at all, so it costs the sender no retry budget.
        assertEquals(RemoteConfigAckResponse.NotAddressable, response)
        assertTrue(gateway.batches.isEmpty())
        assertTrue(gateway.sessions.isEmpty())
    }

    @Test
    fun `a batch the gateway could never accept is refused without a request`() {
        assertEquals(RemoteConfigAckResponse.Permanent, post(emptyList()))
        assertEquals(
            RemoteConfigAckResponse.Permanent,
            post(List(REMOTE_CONFIG_TELEMETRY_MAX_BATCH_EVENTS + 1) { decodeFailure("key-$it") }),
        )
        assertTrue(gateway.batches.isEmpty())
    }

    @Test
    fun `a transport fault is retryable`() {
        server.shutdown()

        assertEquals(RemoteConfigAckResponse.Retryable, post(listOf(decodeFailure("paywall_prices"))))
    }

    private fun postWith(statusCode: Int): RemoteConfigAckResponse {
        gateway.script(statusCode)
        return post(listOf(decodeFailure("paywall_prices")))
    }

    private fun post(events: List<RemoteConfigTelemetryEvent>): RemoteConfigAckResponse {
        val latch = CountDownLatch(1)
        val result = AtomicReference<RemoteConfigAckResponse>()
        transport().postTelemetry(SCOPE_A, events) { response ->
            result.set(response)
            latch.countDown()
        }
        assertTrue("the telemetry post never completed", latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun decodeFailure(key: String) = RemoteConfigTelemetryEvent(
        kind = RemoteConfigTelemetryKind.DecodeFailure,
        logicalKey = key,
        releaseNumber = RELEASE_7,
        count = 1,
        lastOccurredAtSeconds = OCCURRED_AT_SECONDS,
    )

    private fun guardEvent() = RemoteConfigTelemetryEvent(
        kind = RemoteConfigTelemetryKind.ReadBeforeActivate,
        logicalKey = "",
        releaseNumber = 0,
        count = 2,
        lastOccurredAtSeconds = OCCURRED_AT_SECONDS,
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
        clock = { NOW_MILLIS },
        moshi = Moshi.Builder().build(),
        logger = SilentLogger(),
    )

    private data class RecordedBatch(
        val method: String,
        val path: String?,
        val body: String,
        val sessionHeader: String?,
        val authorization: String?,
        val cacheControl: String?,
    )

    private class ScriptedGateway : Dispatcher() {
        val batches: MutableList<RecordedBatch> = Collections.synchronizedList(mutableListOf())
        val sessions: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val statuses = ArrayDeque<Int>()

        /** Answers the next batches with [scripted], then `204` forever. */
        fun script(vararg scripted: Int) {
            synchronized(statuses) { scripted.forEach(statuses::addLast) }
        }

        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            RC_SESSION_PATH -> {
                sessions += request.body.readUtf8()
                MockResponse().setResponseCode(HTTP_OK).setBody(
                    "{\"session_token\":\"$SESSION_TOKEN\",\"project_id\":$RC_PROJECT_ID," +
                        "\"environment\":\"prod\",\"expires_at\":\"2030-01-01T00:00:00Z\"}",
                )
            }
            RC_TELEMETRY_PATH -> {
                batches += RecordedBatch(
                    method = request.method.orEmpty(),
                    path = request.path,
                    body = request.body.readUtf8(),
                    sessionHeader = request.getHeader(REMOTE_CONFIG_SESSION_HEADER),
                    authorization = request.getHeader("Authorization"),
                    cacheControl = request.getHeader("Cache-Control"),
                )
                MockResponse().setResponseCode(
                    synchronized(statuses) { statuses.pollFirst() } ?: HTTP_NO_CONTENT,
                )
            }
            else -> MockResponse().setResponseCode(HTTP_NOT_FOUND)
        }
    }

    private companion object {
        val SCOPE_A = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "QON_anon_a")
        val SCOPE_B = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "QON_anon_b")
    }
}
