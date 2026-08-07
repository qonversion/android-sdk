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
        val core = RemoteConfigSnapshotCore(store, bundledRelease(application, primaryConfig.projectKey))
        // One single-threaded worker for BOTH the preloader and the manager: the manager's
        // ordering contract (preload installs before a binding change observes the scope) is
        // exactly this executor's FIFO ordering.
        val worker = Executors.newSingleThreadExecutor(daemonThreadFactory(REMOTE_CONFIG_V2_WORKER_THREAD_NAME))
        val scheduler = scheduler()
        val readGuard = RemoteConfigReadGuard(
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
            telemetry = { event -> logger.debug("Remote Config v2 guard event: $event") },
        )
        val scopeHolder = RemoteConfigV2ScopeHolder()
        val clock = RemoteConfigFetchClock { System.currentTimeMillis() }
        val coordinator = RemoteConfigFetchCoordinator(
            core = core,
            transport = transport(application, internalConfig, config, scopeHolder, cache, moshi, logger, clock),
            policyStore = PersistentRemoteConfigFetchPolicyStore(cache, moshi),
            clock = clock,
            random = { Random.Default.nextDouble() },
            scheduler = scheduler,
            policy = RemoteConfigFetchPolicy(
                minimumFetchIntervalMillis = REMOTE_CONFIG_V2_MINIMUM_FETCH_INTERVAL_MILLIS,
                // A backstop above the per-call waits: it releases waiters that joined a request
                // the socket timeouts somehow outlived, so one wedged call cannot park later ones.
                timeoutMillis = REMOTE_CONFIG_V2_REQUEST_TIMEOUT_MILLIS,
            ),
        )
        return RemoteConfigV2Manager(
            core = core,
            readGuard = readGuard,
            coordinator = coordinator,
            options = RemoteConfigV2Options(
                projectKey = primaryConfig.projectKey,
                environmentUid = config.environmentUid,
                projectId = config.projectId,
            ),
            scopeHolder = scopeHolder,
            scheduler = scheduler,
            worker = worker,
            mainDispatcher = mainDispatcher,
            logger = logger,
        )
    }

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
                )
            }
        },
        clientContextProvider = DeviceRemoteConfigClientContextProvider(
            context = application,
            sdkVersion = internalConfig.primaryConfig.sdkVersion,
        ),
        sessionStore = PersistentRemoteConfigSessionStore(cache, moshi),
        clock = clock,
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
