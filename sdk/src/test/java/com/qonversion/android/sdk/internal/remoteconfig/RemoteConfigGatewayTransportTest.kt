package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * HTTP contract tests for [RemoteConfigGatewayTransport] against a real [MockWebServer].
 *
 * The SDK previously had no HTTP fixture at all (see `RedemptionManagerTest`, which hand-mocks
 * Retrofit calls). This adapter is byte-exact by contract, so a real socket is the only fixture
 * that can actually prove the bytes and headers on the wire.
 */
internal class RemoteConfigGatewayTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var cache: MapCache
    private lateinit var logger: RecordingLogger
    private val clock = MutableClock(1_000_000)
    private var identity: RemoteConfigTransportIdentity? = identityFor(SCOPE_A, USER_A)
    private var clientContext = CLIENT_CONTEXT

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        cache = MapCache()
        logger = RecordingLogger()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `bootstrap and snapshot requests match the gateway contract exactly`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        val response = fetch(RemoteConfigFetchRequest())

        val bootstrap = server.takeRequest()
        assertEquals("POST", bootstrap.method)
        assertEquals("/v3/remote-config-v2/session", bootstrap.path)
        assertEquals("Bearer $PROJECT_TOKEN", bootstrap.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", bootstrap.getHeader("Content-Type"))
        assertEquals("{\"user_uid\":\"$USER_A\"}", bootstrap.body.readUtf8())
        assertNull(bootstrap.getHeader(REMOTE_CONFIG_SESSION_HEADER))

        val snapshot = server.takeRequest()
        assertEquals("POST", snapshot.method)
        assertEquals("/v3/remote-config-v2/snapshot", snapshot.path)
        assertEquals("Bearer $PROJECT_TOKEN", snapshot.getHeader("Authorization"))
        assertEquals(SESSION_TOKEN, snapshot.getHeader(REMOTE_CONFIG_SESSION_HEADER))
        assertNull(snapshot.getHeader("If-None-Match"))
        assertEquals(
            "{\"client_context\":{\"platform\":\"android\",\"app_version\":\"1.2.3\"," +
                "\"os_version\":\"14\",\"sdk_version\":\"9.7.0\",\"locale\":\"en_US\"," +
                "\"device_model\":\"Pixel 8\",\"device_installed_at\":1577836800}}",
            snapshot.body.readUtf8(),
        )
        assertTrue(response is RemoteConfigFetchResponse.Success)
    }

    @Test
    fun `conditional validator is forwarded verbatim as If-None-Match`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", SNAPSHOT_ETAG))

        val response = fetch(RemoteConfigFetchRequest(ifNoneMatch = SNAPSHOT_ETAG))

        server.takeRequest()
        assertEquals(SNAPSHOT_ETAG, server.takeRequest().getHeader("If-None-Match"))
        assertEquals(RemoteConfigFetchResponse.NotModified(SNAPSHOT_ETAG), response)
    }

    @Test
    fun `200 hands back the exact response bytes and etag`() {
        // Deliberately non-canonical: padded whitespace, an escaped code point and a raw
        // multi-byte character. Any re-encode or charset round trip changes these bytes and
        // therefore the sha256 the ETag pins.
        val body = ("{ \"schema_version\" : 1,  \"note\":\"\\u00e9 café \\ud83d\\ude00\"," +
            "\"trailing\":  true }").toByteArray(Charsets.UTF_8)
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(body, SNAPSHOT_ETAG))

        val response = fetch(RemoteConfigFetchRequest())

        val success = response as RemoteConfigFetchResponse.Success
        assertArrayEquals(body, success.body)
        assertEquals(SNAPSHOT_ETAG, success.etag)
    }

    @Test
    fun `200 without a strong validator is a typed failure`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
    }

    @Test
    fun `snapshot 401 re-bootstraps once and retries successfully`() {
        persistSession(KEY_A, "stale-token")
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"))
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        val response = fetch(RemoteConfigFetchRequest())

        assertTrue(response is RemoteConfigFetchResponse.Success)
        val first = server.takeRequest()
        assertEquals("/v3/remote-config-v2/snapshot", first.path)
        assertEquals("stale-token", first.getHeader(REMOTE_CONFIG_SESSION_HEADER))
        assertEquals("/v3/remote-config-v2/session", server.takeRequest().path)
        val retry = server.takeRequest()
        assertEquals("/v3/remote-config-v2/snapshot", retry.path)
        assertEquals(SESSION_TOKEN, retry.getHeader(REMOTE_CONFIG_SESSION_HEADER))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `two consecutive 401s fail typed without looping`() {
        persistSession(KEY_A, "stale-token")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(401))

        val response = fetch(RemoteConfigFetchRequest())

        assertEquals(RemoteConfigFetchResponse.Failure(statusCode = 401), response)
        assertEquals(3, server.requestCount)
        assertNull(store().load(KEY_A))
    }

    @Test
    fun `a freshly minted session never re-bootstraps on 401`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(401))

        val response = fetch(RemoteConfigFetchRequest())

        assertEquals(RemoteConfigFetchResponse.Failure(statusCode = 401), response)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `bootstrap 404 and 503 surface as typed failures`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"not found\"}"))
        assertEquals(RemoteConfigFetchResponse.Failure(statusCode = 404), fetch(RemoteConfigFetchRequest()))

        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"))
        assertEquals(RemoteConfigFetchResponse.Failure(statusCode = 503), fetch(RemoteConfigFetchRequest()))
    }

    @Test
    fun `snapshot 404 and 503 surface as typed failures and 503 honours Retry-After`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(RemoteConfigFetchResponse.Failure(statusCode = 404), fetch(RemoteConfigFetchRequest()))

        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "7"))
        assertEquals(
            RemoteConfigFetchResponse.Failure(statusCode = 503, retryAfterMillis = 7_000),
            fetch(RemoteConfigFetchRequest()),
        )
    }

    @Test
    fun `a broken connection is an untyped failure rather than a crash`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
    }

    @Test
    fun `session is persisted per identity scope and never reused after an identity change`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))
        val transport = transport()
        fetch(RemoteConfigFetchRequest(), transport)
        server.takeRequest()
        server.takeRequest()
        val keysAfterFirstIdentity = cache.strings.keys.toSet()
        assertEquals(1, keysAfterFirstIdentity.size)
        assertFalse(keysAfterFirstIdentity.single().contains(USER_A))
        assertFalse(keysAfterFirstIdentity.single().contains(PROJECT_TOKEN))

        identity = identityFor(SCOPE_B, USER_B)
        server.enqueue(sessionResponse(OTHER_SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))
        fetch(RemoteConfigFetchRequest(), transport)

        val bootstrap = server.takeRequest()
        assertEquals("/v3/remote-config-v2/session", bootstrap.path)
        assertEquals("{\"user_uid\":\"$USER_B\"}", bootstrap.body.readUtf8())
        val snapshot = server.takeRequest()
        assertEquals(OTHER_SESSION_TOKEN, snapshot.getHeader(REMOTE_CONFIG_SESSION_HEADER))
        assertEquals(2, cache.strings.size)
        assertEquals(SESSION_TOKEN, store().load(KEY_A)?.token)
        assertEquals(OTHER_SESSION_TOKEN, store().load(KEY_B)?.token)
    }

    @Test
    fun `a persisted session is reused without another bootstrap until it expires`() {
        persistSession(KEY_A, SESSION_TOKEN, expiresAtMillis = clock.now + 3_600_000)
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        assertTrue(fetch(RemoteConfigFetchRequest()) is RemoteConfigFetchResponse.Success)
        assertEquals(1, server.requestCount)
        assertEquals("/v3/remote-config-v2/snapshot", server.takeRequest().path)
    }

    @Test
    fun `an expired persisted session is dropped and re-bootstrapped`() {
        persistSession(KEY_A, "expired-token", expiresAtMillis = clock.now - 1)
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        assertTrue(fetch(RemoteConfigFetchRequest()) is RemoteConfigFetchResponse.Success)
        assertEquals("/v3/remote-config-v2/session", server.takeRequest().path)
        assertEquals(SESSION_TOKEN, server.takeRequest().getHeader(REMOTE_CONFIG_SESSION_HEADER))
    }

    @Test
    fun `device_installed_at is unchanged across a simulated logout`() {
        // Logout mints a brand new anonymous uid and therefore a brand new snapshot scope. The
        // device install date must not move with it: the server takes
        // min(device_installed_at, client.created_at), so a moving value would make a long-time
        // user look brand new to "new users" targeting.
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))
        fetch(RemoteConfigFetchRequest())
        server.takeRequest()
        val beforeLogout = server.takeRequest().body.readUtf8()

        identity = identityFor(SCOPE_B, USER_B)
        server.enqueue(sessionResponse(OTHER_SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))
        fetch(RemoteConfigFetchRequest())
        server.takeRequest()
        val afterLogout = server.takeRequest().body.readUtf8()

        assertTrue(beforeLogout.contains("\"device_installed_at\":1577836800"))
        assertEquals(beforeLogout, afterLogout)
    }

    @Test
    fun `neither the project token nor the session token is ever logged`() {
        persistSession(KEY_A, "stale-token")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(401))
        fetch(RemoteConfigFetchRequest())

        server.enqueue(MockResponse().setResponseCode(404))
        fetch(RemoteConfigFetchRequest())

        assertTrue(logger.messages.isNotEmpty())
        logger.messages.forEach { message ->
            assertFalse(message, message.contains(PROJECT_TOKEN))
            assertFalse(message, message.contains(SESSION_TOKEN))
            assertFalse(message, message.contains("stale-token"))
        }
    }

    @Test
    fun `an unaddressable identity fails closed without touching the network`() {
        identity = null
        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a client context the gateway would reject fails closed without touching the network`() {
        clientContext = CLIENT_CONTEXT.copy(deviceInstalledAtSeconds = -1)
        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a bootstrap response with an unusable token is rejected without being persisted`() {
        server.enqueue(sessionResponse(" padded-token "))

        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
        assertNull(store().load(KEY_A))
        // No snapshot may be attempted with a token we refused.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a token that is not a legal header value is refused instead of thrown`() {
        // OkHttp throws IllegalArgumentException for a non-printable header value, and its message
        // quotes the value — i.e. the credential — so this must never reach Request.Builder.
        server.enqueue(sessionResponse("session\u0001secret"))

        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
        assertEquals(1, server.requestCount)
        assertNull(store().load(KEY_A))
        logger.messages.forEach { assertFalse(it, it.contains("secret")) }
    }

    @Test
    fun `a project token that is not a legal header value fails closed before any request`() {
        identity = RemoteConfigTransportIdentity(SCOPE_A, "project\u0001secret", USER_A)

        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a fractional or lowercase RFC3339 expiry is understood and persisted`() {
        // Go marshals time.Time as RFC3339Nano with trailing zeros stripped, so the fraction is
        // 0-9 digits wide; RFC 3339 also permits a lowercase t and z.
        server.enqueue(sessionResponseWithExpiry("2030-01-01t00:00:00.123456789z"))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        assertTrue(fetch(RemoteConfigFetchRequest()) is RemoteConfigFetchResponse.Success)

        assertEquals(1_893_456_000_123, store().load(KEY_A)?.expiresAtMillis)
    }

    @Test
    fun `an over-budget snapshot body is refused rather than allocated`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(ByteArray(65) { '{'.code.toByte() }, SNAPSHOT_ETAG))

        assertEquals(
            RemoteConfigFetchResponse.Failure(),
            fetch(RemoteConfigFetchRequest(), transport(maxSnapshotBodyBytes = 64)),
        )
    }

    @Test
    fun `an empty 200 body is a typed failure rather than an empty admission`() {
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", SNAPSHOT_ETAG))

        assertEquals(RemoteConfigFetchResponse.Failure(), fetch(RemoteConfigFetchRequest()))
    }

    @Test
    fun `one transport reuses its in-memory session across fetches`() {
        val transport = transport(sessionStore = RefusingSessionStore())
        server.enqueue(sessionResponse(SESSION_TOKEN))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        assertTrue(fetch(RemoteConfigFetchRequest(), transport) is RemoteConfigFetchResponse.Success)
        assertTrue(fetch(RemoteConfigFetchRequest(), transport) is RemoteConfigFetchResponse.Success)

        // Bootstrap, snapshot, snapshot: the second fetch reused the cached session even though
        // the durable store refuses to keep anything.
        assertEquals(3, server.requestCount)
        assertEquals("/v3/remote-config-v2/session", server.takeRequest().path)
        assertEquals("/v3/remote-config-v2/snapshot", server.takeRequest().path)
        assertEquals("/v3/remote-config-v2/snapshot", server.takeRequest().path)
    }

    @Test
    fun `concurrent fetches on one transport each get exactly one answer`() {
        server.dispatcher = PathDispatcher()
        val transport = transport()
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREADS)
        val responses = Collections.synchronizedList(mutableListOf<RemoteConfigFetchResponse>())
        repeat(THREADS) {
            Thread {
                start.await()
                transport.fetch(RemoteConfigFetchRequest()) { response ->
                    responses += response
                    done.countDown()
                }
            }.start()
        }
        start.countDown()

        assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(THREADS, responses.size)
        responses.forEach { assertTrue(it.toString(), it is RemoteConfigFetchResponse.Success) }
    }

    @Test
    fun `a session with an unparsable expiry serves the fetch but is not persisted`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"session_token\":\"$SESSION_TOKEN\",\"project_id\":42," +
                    "\"environment\":\"prod\",\"expires_at\":\"x\"}",
            ),
        )
        server.enqueue(snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG))

        assertTrue(fetch(RemoteConfigFetchRequest()) is RemoteConfigFetchResponse.Success)
        server.takeRequest()
        assertEquals(SESSION_TOKEN, server.takeRequest().getHeader(REMOTE_CONFIG_SESSION_HEADER))
        assertNull(store().load(KEY_A))
    }

    private fun fetch(
        request: RemoteConfigFetchRequest,
        transport: RemoteConfigGatewayTransport = transport(),
    ): RemoteConfigFetchResponse {
        val latch = CountDownLatch(1)
        var received: RemoteConfigFetchResponse? = null
        transport.fetch(request) { response ->
            received = response
            latch.countDown()
        }
        assertTrue("transport did not answer in time", latch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        return requireNotNull(received)
    }

    private fun transport(
        sessionStore: RemoteConfigSessionStore = store(),
        maxSnapshotBodyBytes: Long = REMOTE_CONFIG_SNAPSHOT_BODY_MAX_BYTES,
    ) = RemoteConfigGatewayTransport(
        callFactory = client,
        baseUrlProvider = { server.url("/").toString() },
        identityProvider = { identity },
        clientContextProvider = { clientContext },
        sessionStore = sessionStore,
        clock = clock,
        moshi = Moshi.Builder().build(),
        logger = logger,
        maxSnapshotBodyBytes = maxSnapshotBodyBytes,
    )

    private fun store() = PersistentRemoteConfigSessionStore(cache, Moshi.Builder().build())

    private fun persistSession(
        key: RemoteConfigSessionKey,
        token: String,
        expiresAtMillis: Long = clock.now + 3_600_000,
    ) {
        assertTrue(
            store().save(
                key,
                RemoteConfigGatewaySession(
                    token = token,
                    projectId = 42,
                    environment = "prod",
                    expiresAtMillis = expiresAtMillis,
                ),
            ),
        )
    }

    private fun sessionResponse(token: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Cache-Control", "private, no-store")
        .setBody(
            "{\"session_token\":\"$token\",\"project_id\":42,\"environment\":\"prod\"," +
                "\"expires_at\":\"2030-01-01T00:00:00Z\"}",
        )

    private fun sessionResponseWithExpiry(expiresAt: String) = MockResponse()
        .setResponseCode(200)
        .setBody(
            "{\"session_token\":\"$SESSION_TOKEN\",\"project_id\":42,\"environment\":\"prod\"," +
                "\"expires_at\":\"$expiresAt\"}",
        )

    private fun snapshotResponse(body: ByteArray, etag: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("ETag", etag)
        .setHeader("Content-Type", "application/json")
        .setBody(Buffer().write(body))

    private fun identityFor(scope: RemoteConfigSnapshotScope, userUid: String) =
        RemoteConfigTransportIdentity(scope, PROJECT_TOKEN, userUid)

    /** Answers by path so concurrent calls are not order-coupled. */
    private inner class PathDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            if (request.path.orEmpty().endsWith("/session")) {
                sessionResponse(SESSION_TOKEN)
            } else {
                snapshotResponse(SNAPSHOT_BODY, SNAPSHOT_ETAG)
            }
    }

    private class RefusingSessionStore : RemoteConfigSessionStore {
        override fun load(key: RemoteConfigSessionKey): RemoteConfigGatewaySession? = null
        override fun save(key: RemoteConfigSessionKey, session: RemoteConfigGatewaySession) = false
        override fun clear(key: RemoteConfigSessionKey) = false
    }

    private class MutableClock(var now: Long) : RemoteConfigFetchClock {
        override fun nowMillis(): Long = now
    }

    private class RecordingLogger : Logger {
        val messages = mutableListOf<String>()
        override fun error(message: String) { messages += message }
        override fun warn(message: String) { messages += message }
        override fun release(message: String) { messages += message }
        override fun debug(message: String) { messages += message }
    }

    private class MapCache : Cache {
        val strings = mutableMapOf<String, String?>()

        override fun putInt(key: String, value: Int) = Unit
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getBool(key: String, defValue: Boolean): Boolean = defValue
        override fun putBool(key: String, value: Boolean) = Unit
        override fun putFloat(key: String, value: Float) = Unit
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun putLong(key: String, value: Long) = Unit
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun putString(key: String, value: String?) { strings[key] = value }
        override fun getString(key: String, defValue: String?): String? = strings[key] ?: defValue
        override fun remove(key: String) { strings.remove(key) }

        override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean {
            removedKeys.forEach(strings::remove)
            strings.putAll(values)
            return true
        }

        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) = Unit
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? = null
    }

    private companion object {
        const val AWAIT_SECONDS = 10L
        const val THREADS = 4
        const val PROJECT_TOKEN = "project-key-secret"
        const val SESSION_TOKEN = "qrcs1.session-secret"
        const val OTHER_SESSION_TOKEN = "qrcs1.other-session-secret"
        const val USER_A = "QON_anon_a"
        const val USER_B = "QON_anon_b"
        val SCOPE_A = RemoteConfigSnapshotScope("project", "env-production", USER_A)
        val SCOPE_B = RemoteConfigSnapshotScope("project", "env-production", USER_B)
        val KEY_A = RemoteConfigSessionKey(SCOPE_A, USER_A)
        val KEY_B = RemoteConfigSessionKey(SCOPE_B, USER_B)
        val SNAPSHOT_BODY = "{\"schema_version\":1}".toByteArray(Charsets.UTF_8)
        const val SNAPSHOT_ETAG = "\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\""
        val CLIENT_CONTEXT = RemoteConfigClientContext(
            platform = "android",
            appVersion = "1.2.3",
            osVersion = "14",
            sdkVersion = "9.7.0",
            locale = "en_US",
            deviceModel = "Pixel 8",
            deviceInstalledAtSeconds = 1_577_836_800,
        )
    }
}
