@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.QRemoteConfigSnapshots
import com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigUpdate
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.services.decodePortableRemoteConfigJson
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal const val RC_PROJECT_KEY = "project-key"
internal const val RC_ENVIRONMENT = "production"
internal const val RC_PROJECT_ID = 42L
internal const val RC_FINGERPRINT_LENGTH = 64
internal const val RC_AWAIT_SECONDS = 10L
internal const val RC_MAIN_THREAD_NAME = "qonversion-test-main"
internal const val RC_SESSION_PATH = "/v3/remote-config-v2/session"
internal const val RC_SNAPSHOT_PATH = "/v3/remote-config-v2/snapshot"
internal const val RC_DEVICE_INSTALLED_AT = 1_577_836_800L
internal const val HTTP_OK = 200
internal const val HTTP_NOT_MODIFIED = 304
internal const val HTTP_SERVER_ERROR = 500

internal val RC_FINGERPRINT = "a".repeat(RC_FINGERPRINT_LENGTH)

/** One value of a scripted snapshot release. */
internal data class RcWireValue(
    val key: String,
    val raw: String,
    val applyPolicy: String = "on_next_activate",
    val metadata: String = "null",
) {
    fun toJson(): String = "\"$key\":{\"raw\":$raw,\"variation_uid\":\"var-$key-$applyPolicy\"," +
        "\"apply_policy\":\"$applyPolicy\",\"metadata\":$metadata}"
}

internal fun rcWireBody(
    releaseUid: String,
    releaseNumber: Long,
    values: List<RcWireValue>,
    contextFingerprint: String = RC_FINGERPRINT,
): String = "{\"schema_version\":1,\"project_id\":$RC_PROJECT_ID," +
    "\"environment_uid\":\"$RC_ENVIRONMENT\",\"release_uid\":\"$releaseUid\"," +
    "\"release_number\":$releaseNumber,\"manifest_content_hash\":\"${"1".repeat(RC_FINGERPRINT_LENGTH)}\"," +
    "\"complete_key_set\":true,\"context_fingerprint\":\"$contextFingerprint\"," +
    "\"values\":{${values.joinToString(",") { it.toJson() }}}}"

internal fun rcStrongETag(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(body)
    .joinToString(prefix = "\"", postfix = "\"", separator = "") { byte -> "%02x".format(byte) }

/**
 * The Remote Config defaults "bundled with the app" for these tests.
 *
 * `count` is also served by every scripted release, so it can be observed at all three ladder
 * positions; `bundled_only` exists nowhere else, so it can only ever resolve to the fallback.
 */
internal fun rcBundledRelease() = RemoteConfigScopedBundledRelease(
    projectKey = RC_PROJECT_KEY,
    environment = RC_ENVIRONMENT,
    release = RemoteConfigSnapshotRelease(
        releaseUid = "bundled-release",
        releaseNumber = 1,
        manifestContentHash = "2".repeat(RC_FINGERPRINT_LENGTH),
        entries = listOf(
            RemoteConfigSnapshotEntry.value(
                key = "count",
                rawValue = "0".encodeToByteArray(),
                variationUid = "bundled-count",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = null,
            ),
            RemoteConfigSnapshotEntry.value(
                key = "bundled_only",
                rawValue = "\"bundled\"".encodeToByteArray(),
                variationUid = "bundled-only",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = null,
            ),
        ),
    ),
)

/**
 * Builds the real chain behind the public API — snapshot core, read guard, fetch coordinator and
 * the gateway transport over a real [MockWebServer] — so the tests exercise the shipped wiring
 * rather than a mock of it.
 *
 * Only three things are test doubles, each for determinism rather than convenience: the snapshot /
 * policy stores are in memory, the fetch timeout scheduler is manual, and the "main thread" is a
 * single named executor so callback threading can be asserted.
 */
@Suppress("LongParameterList")
internal class RemoteConfigV2Harness(
    buildMode: RemoteConfigReadBuildMode = RemoteConfigReadBuildMode.Debug,
    bundled: RemoteConfigScopedBundledRelease? = rcBundledRelease(),
    defaultFetchTimeoutMillis: Long = 0,
    minimumFetchIntervalMillis: Long = 0,
    clientContextProvider: RemoteConfigClientContextProvider = RemoteConfigClientContextProvider {
        RemoteConfigClientContext(
            platform = "android",
            appVersion = "1.2.3",
            osVersion = "14",
            sdkVersion = "9.7.0",
            locale = "en_US",
            deviceModel = "Pixel 8",
            deviceInstalledAtSeconds = RC_DEVICE_INSTALLED_AT,
        )
    },
) {
    private val bundledEntries = bundled?.release
    private val httpClient = OkHttpClient()
    val server = MockWebServer()
    val snapshotStore = InMemorySnapshotStore()
    val timeoutScheduler = ManualScheduler()
    val assertions: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val guardEvents: MutableList<RemoteConfigReadGuardEvent> = Collections.synchronizedList(mutableListOf())
    val snapshotRequests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val sessionRequests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var userUid: String = "QON_anon_a"

    private val body = AtomicReference(defaultBody())
    private val hang = AtomicReference(false)
    private val responseDelayMillis = AtomicReference(0L)
    private val snapshotStatusCode = AtomicReference(HTTP_OK)
    private val contextFingerprint = AtomicReference(RC_FINGERPRINT)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qonversion-test-worker")
    }
    private val mainExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, RC_MAIN_THREAD_NAME)
    }
    private val scopeHolder = RemoteConfigV2ScopeHolder()
    private val mainDispatcher = RemoteConfigMainDispatcher { action -> mainExecutor.execute(action) }

    val core = RemoteConfigSnapshotCore(snapshotStore, bundled)

    private val readGuard = RemoteConfigReadGuard(
        core = core,
        preloader = PersistentRemoteConfigReadPreloader(snapshotStore, worker),
        buildMode = buildMode,
        assertion = { message -> assertions += message },
        telemetry = { event -> guardEvents += event },
    )

    val coordinator = RemoteConfigFetchCoordinator(
        core = core,
        transport = RemoteConfigGatewayTransport(
            callFactory = httpClient,
            baseUrlProvider = { server.url("/").toString() },
            identityProvider = {
                scopeHolder.scope?.let { scope ->
                    RemoteConfigTransportIdentity(scope, "project-token", userUid)
                }
            },
            clientContextProvider = clientContextProvider,
            sessionStore = InMemorySessionStore(),
            projectIds = RemoteConfigProjectIdRegistry(InMemoryProjectIdStore()),
            clock = { System.currentTimeMillis() },
            moshi = Moshi.Builder().build(),
            logger = SilentLogger(),
        ),
        policyStore = InMemoryFetchPolicyStore(),
        clock = { System.currentTimeMillis() },
        random = { 0.5 },
        // The coordinator's own timeout is disabled: the public API's per-call timeout is the
        // behaviour under test, and a second timer would make which one fired ambiguous.
        scheduler = { _, _ -> RemoteConfigFetchScheduledTask { } },
        policy = RemoteConfigFetchPolicy(
            minimumFetchIntervalMillis = minimumFetchIntervalMillis,
            timeoutMillis = null,
        ),
    )

    val manager = RemoteConfigV2Manager(
        core = core,
        readGuard = readGuard,
        coordinator = coordinator,
        options = RemoteConfigV2Options(RC_PROJECT_KEY, RC_ENVIRONMENT),
        scopeHolder = scopeHolder,
        scheduler = timeoutScheduler,
        worker = worker,
        mainDispatcher = mainDispatcher,
        logger = SilentLogger(),
        defaultFetchTimeoutMillis = defaultFetchTimeoutMillis,
    )

    val configs: QRemoteConfigSnapshots = QRemoteConfigSnapshotsImpl(
        manager = manager,
        bundledValueReader = { contextKey -> fallbackRemoteConfigValue(contextKey) },
        mainDispatcher = mainDispatcher,
    )

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                RC_SESSION_PATH -> {
                    sessionRequests += request.body.readUtf8()
                    sessionResponse()
                }
                RC_SNAPSHOT_PATH -> {
                    snapshotRequests += request.body.readUtf8()
                    snapshotResponse()
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    fun shutdown() {
        server.shutdown()
        worker.shutdownNow()
        mainExecutor.shutdownNow()
        httpClient.dispatcher().executorService().shutdownNow()
        httpClient.connectionPool().evictAll()
    }

    /** Scripts the release the gateway serves from now on. */
    fun serve(releaseUid: String, releaseNumber: Long, values: List<RcWireValue>) {
        body.set(rcWireBody(releaseUid, releaseNumber, values, contextFingerprint.get()))
    }

    /** Makes the gateway answer snapshot reads with [statusCode] instead of a release. */
    fun serveStatus(statusCode: Int) = snapshotStatusCode.set(statusCode)

    /**
     * Rotates the targeting context the gateway reports, as it does for real when the app version,
     * locale, purchases, properties or experiment enrollment change.
     */
    fun rotateContextFingerprint() {
        contextFingerprint.set("b".repeat(RC_FINGERPRINT_LENGTH))
        body.set(
            rcWireBody(
                releaseUid = "release-rotated",
                releaseNumber = ROTATED_RELEASE_NUMBER,
                values = listOf(RcWireValue("count", "2")),
                contextFingerprint = contextFingerprint.get(),
            ),
        )
    }

    /** Makes the gateway stop answering snapshot reads, without closing the socket. */
    fun hangSnapshotReads(hanging: Boolean) = hang.set(hanging)

    /** Delays the snapshot answer, so a caller-side timeout can win the race deterministically. */
    fun delaySnapshotReads(millis: Long) = responseDelayMillis.set(millis)

    fun identify(userUid: String, canonicalUserId: String, reason: RemoteConfigFetchForceReason) {
        this.userUid = userUid
        manager.updateIdentity(canonicalUserId, reason)
    }

    fun fetchBlocking(timeoutMs: Long? = null): QRemoteConfigFetchResult {
        val latch = CountDownLatch(1)
        val result = AtomicReference<QRemoteConfigFetchResult>()
        val thread = AtomicReference<String>()
        manager.fetch(timeoutMs) { fetchResult ->
            thread.set(Thread.currentThread().name)
            result.set(fetchResult)
            latch.countDown()
        }
        assertTrue("fetch did not complete", latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(RC_MAIN_THREAD_NAME, thread.get())
        return requireNotNull(result.get())
    }

    fun activateBlocking(): QRemoteConfigActivationResult {
        val latch = CountDownLatch(1)
        val result = AtomicReference<QRemoteConfigActivationResult>()
        val thread = AtomicReference<String>()
        manager.activate { activationResult ->
            thread.set(Thread.currentThread().name)
            result.set(activationResult)
            latch.countDown()
        }
        assertTrue("activation did not complete", latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(RC_MAIN_THREAD_NAME, thread.get())
        return requireNotNull(result.get())
    }

    fun subscribeCollecting(updates: MutableList<QRemoteConfigUpdate>, latch: CountDownLatch) =
        manager.subscribeOnConfigUpdate { update ->
            updates += update
            latch.countDown()
        }

    /** Waits until [releaseNumber] is durably admitted as the fetched candidate. */
    fun awaitCandidate(releaseNumber: Long) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS)
        while (System.currentTimeMillis() < deadline) {
            if (core.lastFetchedSnapshot()?.releaseNumber == releaseNumber) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("release $releaseNumber was never admitted")
    }

    /** Blocks until every task already queued on the background worker has run. */
    fun awaitWorkerIdle() {
        val latch = CountDownLatch(1)
        worker.execute { latch.countDown() }
        assertTrue("worker did not drain", latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
    }

    /**
     * Reads the same bundled entries the snapshot core resolves against, so the "manual fallback
     * getter" and the fallback rung of the ladder can never silently drift apart.
     */
    private fun fallbackRemoteConfigValue(contextKey: String): QRemoteConfigFallbackValue? {
        val raw = bundledEntries?.entry(contextKey)?.rawValueBytes ?: return null
        return QRemoteConfigFallbackValue(
            requireNotNull(decodePortableRemoteConfigJson(raw)).value,
        )
    }

    private fun defaultBody(fingerprint: String = RC_FINGERPRINT) =
        rcWireBody("release-1", 1, listOf(RcWireValue("count", "1")), fingerprint)

    private fun sessionResponse() = MockResponse()
        .setResponseCode(200)
        .setBody(
            "{\"session_token\":\"qrcs1.session-${sessionRequests.size}\",\"project_id\":$RC_PROJECT_ID," +
                "\"environment\":\"prod\",\"expires_at\":\"2030-01-01T00:00:00Z\"}",
        )

    private fun snapshotResponse(): MockResponse {
        if (hang.get()) return MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE)
        val statusCode = snapshotStatusCode.get()
        if (statusCode != HTTP_OK) {
            val response = MockResponse().setResponseCode(statusCode)
            return if (statusCode == HTTP_NOT_MODIFIED) {
                response.setHeader("ETag", rcStrongETag(body.get().toByteArray(Charsets.UTF_8)))
            } else {
                response
            }
        }
        val bytes = body.get().toByteArray(Charsets.UTF_8)
        return MockResponse()
            .setResponseCode(200)
            .setHeader("ETag", rcStrongETag(bytes))
            .setBody(Buffer().write(bytes))
            .setBodyDelay(responseDelayMillis.get(), TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 20L
        const val ROTATED_RELEASE_NUMBER = 2L
    }
}

internal class ManualScheduler : RemoteConfigFetchScheduler {
    private val tasks = mutableListOf<Task>()

    /** Every delay the code under test asked for, in scheduling order. */
    val requestedDelays: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override fun schedule(delayMillis: Long, action: () -> Unit): RemoteConfigFetchScheduledTask {
        val task = Task(action)
        synchronized(tasks) { tasks += task }
        requestedDelays += delayMillis
        return RemoteConfigFetchScheduledTask { task.cancelled = true }
    }

    /** Fires every scheduled task that has not been cancelled yet. */
    fun runAll() {
        val pending = synchronized(tasks) { tasks.toList().also { tasks.clear() } }
        pending.forEach { task -> if (!task.cancelled) task.action() }
    }

    fun pendingCount(): Int = synchronized(tasks) { tasks.count { !it.cancelled } }

    private class Task(val action: () -> Unit, @Volatile var cancelled: Boolean = false)
}

internal class InMemorySnapshotStore : RemoteConfigSnapshotStore {
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

internal class InMemoryFetchPolicyStore : RemoteConfigFetchPolicyStore {
    private val states = mutableMapOf<RemoteConfigFetchPolicyScope, RemoteConfigFetchPolicyState>()

    @Synchronized
    override fun load(scope: RemoteConfigFetchPolicyScope) = states[scope]

    @Synchronized
    override fun save(scope: RemoteConfigFetchPolicyScope, state: RemoteConfigFetchPolicyState): Boolean {
        states[scope] = state
        return true
    }
}

internal class InMemorySessionStore : RemoteConfigSessionStore {
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

internal class InMemoryProjectIdStore : RemoteConfigProjectIdStore {
    private val projectIds = mutableMapOf<Pair<String, String>, Long>()

    @Synchronized
    override fun load(scope: RemoteConfigSnapshotScope): Long? = projectIds[key(scope)]

    @Synchronized
    override fun save(scope: RemoteConfigSnapshotScope, projectId: Long): Boolean {
        projectIds[key(scope)] = projectId
        return true
    }

    private fun key(scope: RemoteConfigSnapshotScope) = scope.projectKey to scope.environment
}

internal class SilentLogger : Logger {
    override fun error(message: String) = Unit
    override fun warn(message: String) = Unit
    override fun release(message: String) = Unit
    override fun debug(message: String) = Unit
}
