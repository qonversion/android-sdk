@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import android.app.Application
import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigV2Config
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.isDebuggable
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaults
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaultsReader
import com.qonversion.android.sdk.internal.storage.Cache
import com.qonversion.android.sdk.internal.storage.PersistentRemoteConfigSnapshotStore
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val REMOTE_CONFIG_V2_MINIMUM_FETCH_INTERVAL_MILLIS = 60_000L
private const val REMOTE_CONFIG_V2_TRANSPORT_TIMEOUT_SECONDS = 15L
private const val REMOTE_CONFIG_V2_REQUEST_TIMEOUT_MILLIS = 30_000L
private const val REMOTE_CONFIG_V2_WORKER_THREAD_NAME = "qonversion-remote-config-v2"
private const val REMOTE_CONFIG_V2_SCHEDULER_THREAD_NAME = "qonversion-remote-config-v2-timer"

/**
 * Builds the whole Remote Config v2 chain, or nothing at all.
 *
 * Nothing is constructed unless the app supplied a [QRemoteConfigV2Config]: no store, no
 * background threads, no HTTP client, no base URL. This is the single switch that keeps the
 * feature dormant, and there is deliberately no default endpoint to fall back to.
 *
 * The subsystem is assembled here rather than in the Dagger graph for the same reason
 * `RedemptionManager` is: it owns its dependencies end to end (cache + moshi + logger + its own
 * OkHttp client), and adding a module for one optional object would put a dormant feature into
 * every graph build.
 */
internal object RemoteConfigV2Factory {

    fun create(
        application: Application,
        internalConfig: InternalConfig,
        cache: Cache,
        logger: Logger,
    ): QRemoteConfigSnapshotsImpl {
        val bundledReader: (String) -> QRemoteConfigFallbackValue? = { contextKey ->
            BundledRemoteConfigDefaults.value(application, contextKey)
        }
        val config = internalConfig.remoteConfigV2Config
        val mainDispatcher = MainThreadDispatcher()
        val manager = config?.let {
            createManager(application, internalConfig, it, cache, logger, mainDispatcher)
        }
        return QRemoteConfigSnapshotsImpl(manager, bundledReader, mainDispatcher)
    }

    @Suppress("LongParameterList")
    private fun createManager(
        application: Application,
        internalConfig: InternalConfig,
        config: QRemoteConfigV2Config,
        cache: Cache,
        logger: Logger,
        mainDispatcher: RemoteConfigMainDispatcher,
    ): RemoteConfigV2Manager {
        val moshi = Moshi.Builder().build()
        val primaryConfig = internalConfig.primaryConfig
        val store = PersistentRemoteConfigSnapshotStore(cache, moshi)
        // One single-threaded worker for BOTH the preloader and the manager: the manager's
        // ordering contract (preload installs before a scope transition is observed) is
        // exactly this executor's FIFO ordering.
        val worker = Executors.newSingleThreadExecutor(daemonThreadFactory(REMOTE_CONFIG_V2_WORKER_THREAD_NAME))
        val scheduler = scheduler()
        val scopeHolder = RemoteConfigV2ScopeHolder()
        val clock = RemoteConfigFetchClock { System.currentTimeMillis() }
        val random = RemoteConfigFetchRandom { Random.Default.nextDouble() }
        // One transport for all three routes: the activation ack and the client telemetry batch
        // ride the very same session, bootstrap and re-bootstrap-once rule as a snapshot read.
        val transport = transport(application, internalConfig, config, scopeHolder, cache, moshi, logger, clock)
        val telemetrySender = telemetrySender(transport, cache, moshi, clock, random, scheduler, worker)
        val core = RemoteConfigSnapshotCore(
            store = store,
            bundledRelease = bundledRelease(application, primaryConfig.projectKey),
            // The only production point for `decode_failure`, and the only one that can exist: the
            // resolution ladder absorbs a failed decode by design, so nothing downstream of the
            // read site can tell a mis-typed key from an absent one.
            decodeFailureObserver = telemetrySender::recordDecodeFailure,
        )
        val readGuard = readGuard(application, core, store, worker, telemetrySender, logger)
        val coordinator = RemoteConfigFetchCoordinator(
            core = core,
            transport = transport,
            policyStore = PersistentRemoteConfigFetchPolicyStore(cache, moshi),
            clock = clock,
            random = random,
            scheduler = scheduler,
            policy = RemoteConfigFetchPolicy(
                minimumFetchIntervalMillis = minimumFetchIntervalMillis(config, application.isDebuggable),
                // A backstop above the per-call waits: it releases waiters that joined a request
                // the socket timeouts somehow outlived, so one wedged call cannot park later ones.
                timeoutMillis = REMOTE_CONFIG_V2_REQUEST_TIMEOUT_MILLIS,
            ),
            // Bookkeeping the next attempt re-derives, so it never surfaces to the app — but it is
            // the one persistence failure the read guard cannot see, and the dashboard counts it
            // with the rest.
            policyPersistenceFailureObserver = { telemetrySender.recordPolicyPersistenceFailure() },
        )
        return RemoteConfigV2Manager(
            core = core,
            readGuard = readGuard,
            coordinator = coordinator,
            ackSender = ackSender(transport, cache, moshi, clock, random, scheduler, worker),
            telemetrySender = telemetrySender,
            options = RemoteConfigV2Options(
                projectKey = primaryConfig.projectKey,
                environmentUid = config.environmentUid,
            ),
            scopeHolder = scopeHolder,
            scheduler = scheduler,
            worker = worker,
            mainDispatcher = mainDispatcher,
            logger = logger,
        )
    }

    /**
     * The effective floor between real network fetches.
     *
     * `0` in the configuration means "auto": the production default in a release build, and no
     * floor at all in a debuggable one, so a developer iterating on an environment sees every
     * change. An explicit positive value wins over auto in both build modes. Forced fetches
     * already bypass the floor, and failure backoff applies independently of it — an interval of
     * zero never disables backoff.
     */
    fun minimumFetchIntervalMillis(config: QRemoteConfigV2Config, isDebuggable: Boolean): Long = when {
        config.minFetchIntervalSeconds > 0 -> TimeUnit.SECONDS.toMillis(config.minFetchIntervalSeconds)
        isDebuggable -> 0L
        else -> REMOTE_CONFIG_V2_MINIMUM_FETCH_INTERVAL_MILLIS
    }

    /**
     * The read guard, with both of its side channels attached.
     *
     * The assertion channel shouts in a debug build; the telemetry channel only ever enqueues,
     * because it is invoked from the app's own read thread.
     */
    @Suppress("LongParameterList")
    private fun readGuard(
        application: Application,
        core: RemoteConfigSnapshotCore,
        store: PersistentRemoteConfigSnapshotStore,
        worker: Executor,
        telemetrySender: RemoteConfigTelemetrySender,
        logger: Logger,
    ) = RemoteConfigReadGuard(
        core = core,
        preloader = PersistentRemoteConfigReadPreloader(store, worker),
        buildMode = if (application.isDebuggable) {
            RemoteConfigReadBuildMode.Debug
        } else {
            RemoteConfigReadBuildMode.Release
        },
        assertion = { message ->
            logger.error(message)
            // Only fires when JVM assertions are enabled, so a debug build shouts without
            // turning a config read into a production crash.
            assert(false) { message }
        },
        telemetry = { event ->
            logger.debug("Remote Config v2 guard event: $event")
            telemetrySender.record(event)
        },
    )

    /**
     * The activation ack queue.
     *
     * It shares the transport (same session, same bootstrap) and the jitter source with the fetch
     * path, but its retries are handed to [worker] rather than run on the timer thread: the timer
     * also releases fetch waiters, and an ack retry does a preferences read and a durable write.
     */
    @Suppress("LongParameterList")
    private fun ackSender(
        transport: RemoteConfigAckTransport,
        cache: Cache,
        moshi: Moshi,
        clock: RemoteConfigFetchClock,
        random: RemoteConfigFetchRandom,
        scheduler: RemoteConfigFetchScheduler,
        worker: Executor,
    ) = RemoteConfigActivationAckSender(
        transport = transport,
        store = PersistentRemoteConfigActivationAckStore(cache, moshi),
        clock = clock,
        random = random,
        scheduler = { delayMillis, action ->
            scheduler.schedule(delayMillis) {
                try {
                    worker.execute(action)
                } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
                    // A shut-down worker simply means this retry is not taken; the ack stays
                    // durable for the next process.
                }
            }
        },
    )

    /**
     * The client telemetry queue.
     *
     * Built exactly like [ackSender] and on purpose: the same transport (same session, same
     * bootstrap), the same jitter source, and retries handed to [worker] rather than to the timer
     * thread, which also releases fetch waiters. [worker] is additionally the executor the sender
     * defers its durable writes to, so a handler invoked from the app's read thread can enqueue an
     * event and return without ever touching storage.
     */
    @Suppress("LongParameterList")
    private fun telemetrySender(
        transport: RemoteConfigTelemetryTransport,
        cache: Cache,
        moshi: Moshi,
        clock: RemoteConfigFetchClock,
        random: RemoteConfigFetchRandom,
        scheduler: RemoteConfigFetchScheduler,
        worker: Executor,
    ) = RemoteConfigTelemetrySender(
        transport = transport,
        store = PersistentRemoteConfigTelemetryStore(cache, moshi),
        clock = clock,
        random = random,
        scheduler = { delayMillis, action ->
            scheduler.schedule(delayMillis) {
                try {
                    worker.execute(action)
                } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
                    // A shut-down worker simply means this flush is not taken; the buffer stays
                    // durable for the next process.
                }
            }
        },
        executor = worker,
    )

    @Suppress("LongParameterList")
    private fun transport(
        application: Application,
        internalConfig: InternalConfig,
        config: QRemoteConfigV2Config,
        scopeHolder: RemoteConfigV2ScopeHolder,
        cache: Cache,
        moshi: Moshi,
        logger: Logger,
        clock: RemoteConfigFetchClock,
    ) = RemoteConfigGatewayTransport(
        // A dedicated client: the shared one carries the legacy NetworkInterceptor, which would
        // append a second Authorization header to requests this transport signs itself.
        callFactory = OkHttpClient.Builder()
            .connectTimeout(REMOTE_CONFIG_V2_TRANSPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REMOTE_CONFIG_V2_TRANSPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REMOTE_CONFIG_V2_TRANSPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build(),
        baseUrlProvider = { config.baseUrl },
        identityProvider = {
            scopeHolder.scope?.let { scope ->
                RemoteConfigTransportIdentity(
                    scope = scope,
                    projectToken = internalConfig.primaryConfig.projectKey,
                    // Read from the scope, not from the live uid: they are the same value by
                    // construction, and reading one source makes it impossible to mint a session
                    // for one identity and admit its snapshot into another identity's store.
                    userUid = scope.canonicalUserId,
                    externalUserId = scopeHolder.externalUserId,
                )
            }
        },
        clientContextProvider = DeviceRemoteConfigClientContextProvider(
            context = application,
            sdkVersion = internalConfig.primaryConfig.sdkVersion,
        ),
        sessionStore = PersistentRemoteConfigSessionStore(cache, moshi),
        // Durable per project key + environment: the project id the first bootstrap established
        // must outlive both the session that carried it and the process that learned it.
        projectIds = RemoteConfigProjectIdRegistry(PersistentRemoteConfigProjectIdStore(cache)),
        clock = clock,
        identifyAssertionProvider = config.identifyAssertionProvider,
        moshi = moshi,
        logger = logger,
    )

    private fun bundledRelease(application: Application, projectKey: String): RemoteConfigScopedBundledRelease? = try {
        BundledRemoteConfigDefaultsReader().read(application)?.toScopedRemoteConfigSnapshotRelease(projectKey)
    } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
        // A malformed bundle degrades the ladder to "no fallback", never to a broken SDK.
        null
    }

    private fun scheduler(): RemoteConfigFetchScheduler {
        val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory(REMOTE_CONFIG_V2_SCHEDULER_THREAD_NAME),
        )
        return RemoteConfigFetchScheduler { delayMillis, action ->
            val future = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            RemoteConfigFetchScheduledTask { future.cancel(false) }
        }
    }

    private fun daemonThreadFactory(name: String) = ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }
}
