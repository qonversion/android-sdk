package com.qonversion.android.sdk.internal

import android.os.Handler
import android.os.Looper
import com.qonversion.android.sdk.dto.QFallbackObject
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.internal.storage.RemoteConfigCache
import com.qonversion.android.sdk.internal.storage.RemoteConfigCacheScope
import com.qonversion.android.sdk.listeners.QonversionEmptyCallback
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private val EmptyContextKey: String? = null

private fun String?.normalizedRemoteConfigContextKey(): String? = takeUnless { it.isNullOrEmpty() }

internal enum class QRemoteConfigDeliveryOrigin {
    Network,
    MemoryCache,
    RetryBaseline,
    PersistentLastKnownGood,
    BundledFallback,
}

private data class RemoteConfigRequestIdentity(
    val userGeneration: Int,
    val cacheScope: RemoteConfigCacheScope?,
)

// Rate-limit tolerance is scoped to remote configs deliberately: the other
// shouldFireFallback consumer (the entitlements path) keeps surfacing
// ApiRateLimitExceeded unchanged. RC requests that are locally short-circuited
// or receive transient HTTP 408/429 responses are exactly the cases the local
// fallback chain exists for. Since fallbacks are no longer memory-cached,
// repeat calls still retry the service whenever the rate limiter permits.
private val QonversionError.shouldFireRemoteConfigFallback
    get(): Boolean {
        if (code in NON_RECOVERABLE_REMOTE_CONFIG_ERRORS) return false

        return shouldFireFallback ||
            code == QonversionErrorCode.ApiRateLimitExceeded ||
            code == QonversionErrorCode.ResponseParsingFailed ||
            httpCode == HTTP_REQUEST_TIMEOUT ||
            httpCode == HTTP_TOO_MANY_REQUESTS
    }

private val NON_RECOVERABLE_REMOTE_CONFIG_ERRORS = setOf(
    QonversionErrorCode.Unknown,
    QonversionErrorCode.InvalidCredentials,
    QonversionErrorCode.InvalidClientUid,
    QonversionErrorCode.UnknownClientPlatform,
    QonversionErrorCode.ProjectConfigError,
    QonversionErrorCode.InvalidStoreCredentials,
)

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429

internal class QRemoteConfigManager @Inject constructor(
    private val remoteConfigService: QRemoteConfigService,
    private val fallbacksService: QFallbacksService,
    private val persistentCache: RemoteConfigCache,
) {
    private val fallbackData: QFallbackObject? by lazy {
        fallbacksService.obtainFallbackData()
    }

    internal class LoadingState(
        var loadedConfig: QRemoteConfig? = null,
        val callbacks: MutableList<QonversionRemoteConfigCallback> = mutableListOf(),
        var isInProgress: Boolean = false,
        // Generation the loadedConfig was cached at — the cached fast paths
        // serve it only while it matches the current invalidation generation,
        // so an invalidation makes the cache stale before the posted
        // main-thread cleanup has run.
        var generation: Int = 0,
        // Last generation a superseded in-flight load was re-issued for —
        // defense-in-depth against a concurrent re-entry; the retry count is
        // bounded structurally by the per-key isInProgress serialisation.
        var reissuedForGeneration: Int? = null,
        // The superseded (but valid) evaluation held while its re-issued
        // retry is in flight. A failed retry degrades to it — for everyone,
        // including callers who join during the retry window — and it
        // outranks the static bundled fallback: a real user-specific
        // evaluation seconds old beats shipped-in-binary defaults.
        var retryBaseline: QRemoteConfig? = null
    )

    internal class ListRequestData(
        val callback: QonversionRemoteConfigListCallback,
        val contextKeys: List<String>? = null,
        val includeEmptyContextKey: Boolean = false
    )

    lateinit var userStateProvider: UserStateProvider
    private var loadingStates = mutableMapOf<String?, LoadingState>()
    private val deliveryOrigins = mutableMapOf<String?, QRemoteConfigDeliveryOrigin>()
    private val listRequests = mutableListOf<ListRequestData>()
    lateinit var userPropertiesManager: QUserPropertiesManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val identityTransitionLock = Any()

    // Bumped on every cache invalidation (attach/detach, user change, explicit
    // invalidateRemoteConfigsCache). Loads capture it when they start and skip
    // the cache write if it moved — an in-flight response evaluated before the
    // invalidating event must not be re-cached as fresh. Atomic rather than
    // main-confined: the explicit invalidation bumps it synchronously on the
    // caller thread, so the cached fast paths reject stale values immediately
    // instead of waiting for the posted main-thread hop to drain.
    private val invalidationGeneration = AtomicInteger(0)
    private val userGeneration = AtomicInteger(0)
    private var appliedUserGeneration = 0

    fun handlePendingRequests() = postIdentityAction {
        loadingStates.filter { it.value.callbacks.isNotEmpty() }
            .keys.forEach { contextKey -> loadRemoteConfig(contextKey, null) }

        // Snapshot then clear before handling: clearing stops stale requests being
        // re-issued on every launch, and iterating the copy keeps a re-entrant add
        // (when the user is still not stable) from mutating the list mid-iteration.
        val pendingListRequests = listRequests.toList()
        listRequests.clear()
        pendingListRequests.forEach { requestData ->
            requestData.contextKeys?.let {
                loadRemoteConfigList(it, requestData.includeEmptyContextKey, requestData.callback)
            } ?: run {
                loadRemoteConfigList(requestData.callback)
            }
        }
    }

    fun userChangingRequestFailedWithError(error: QonversionError) = postIdentityAction {
        // Snapshot the keys: fireToCallbacks runs user callbacks, and a callback that
        // re-enters loadRemoteConfig with a new key runs inline (already on the main thread)
        // and registers that key in loadingStates. Iterating a copy keeps that re-entrant
        // structural add from mutating the map mid-iteration. Main-thread confinement does
        // not help here - the reentrancy is within a single thread.
        loadingStates.keys.toList().forEach { key ->
            // The only drain that bypasses the response handlers — consume
            // the retry stash here too, or a leftover masks a later
            // unrelated failure as a stale success.
            loadingStates[key]?.retryBaseline = null
            fireToCallbacks(key) { onError(error) }
        }
    }

    // Public cache invalidation seam (DEV-1236 B4): marks every cached config
    // stale so the next load fetches a fresh evaluation. Non-destructive —
    // loading states and pending callbacks survive, and the generation bump
    // stops in-flight loads from re-caching a superseded response.
    fun invalidateRemoteConfigsCache() = invalidateOnAnyThread {}

    fun onUserUpdate(updateIdentity: () -> Unit = {}) {
        // The generation and the UID mutation share one linearization point.
        // Loads and response delivery take the same lock, so a background
        // logout/identify cannot expose a half-transitioned cache scope.
        synchronized(identityTransitionLock) {
            invalidationGeneration.incrementAndGet()
            userGeneration.incrementAndGet()
            updateIdentity()
            if (Looper.myLooper() == Looper.getMainLooper()) {
                resetIdentityStateIfNeeded()
            } else {
                mainHandler.post {
                    synchronized(identityTransitionLock) {
                        resetIdentityStateIfNeeded()
                    }
                }
            }
        }
    }

    private fun resetIdentityStateIfNeeded() {
        val currentUserGeneration = userGeneration.get()
        if (appliedUserGeneration == currentUserGeneration) return

        // Move every waiter across the identity boundary before orphaning the
        // old states. Clearing the old callback lists is essential: a late old
        // response still owns those LoadingState instances and must not replay
        // the same waiter a second time.
        val pendingSingleRequests = loadingStates.mapValues { (_, state) ->
            state.callbacks.toList().also { state.callbacks.clear() }
        }.filterValues { it.isNotEmpty() }
        loadingStates = mutableMapOf()
        deliveryOrigins.clear()
        appliedUserGeneration = currentUserGeneration
        pendingSingleRequests.forEach { (contextKey, callbacks) ->
            loadingStates[contextKey] = LoadingState(callbacks = callbacks.toMutableList())
            if (userStateProvider.isUserStable) {
                loadRemoteConfig(contextKey, null)
            }
        }
    }

    internal fun lastDeliveryOrigin(contextKey: String?): QRemoteConfigDeliveryOrigin? =
        synchronized(identityTransitionLock) {
            if (appliedUserGeneration == userGeneration.get()) {
                deliveryOrigins[contextKey.normalizedRemoteConfigContextKey()]
            } else {
                null
            }
        }

    // The explicit Unit is required: the re-issue path recurses into this
    // function, and an inferred expression-body type would depend on itself.
    fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback?): Unit =
        loadRemoteConfigNormalized(contextKey.normalizedRemoteConfigContextKey(), callback)

    private fun loadRemoteConfigNormalized(
        contextKey: String?,
        callback: QonversionRemoteConfigCallback?,
    ): Unit = postIdentityAction {
        loadingStates[contextKey]
            ?.takeIf { it.generation == invalidationGeneration.get() }
            ?.loadedConfig
            ?.takeIf { userStateProvider.isUserStable }
            ?.let { cached ->
                deliveryOrigins[contextKey] = QRemoteConfigDeliveryOrigin.MemoryCache
                // The cached config is served as is, but properties set right
                // before this call must still reach the server (parity with
                // iOS) - a cache hit must not swallow the flush.
                userPropertiesManager.forceSendProperties()
                // A retry resolved by a warm cache consumes its stashed
                // baseline - a leftover stash must not resurface on a later,
                // unrelated failure.
                loadingStates[contextKey]?.retryBaseline = null
                // Queued waiters can be stranded on a warm state: a list load
                // may cache into a state whose own load never fires them (it
                // completed elsewhere or was superseded). Serving only the
                // direct callback would leave them queued forever. Drained
                // inline, NOT via fireToCallbacks: resolving waiters must not
                // mark a still-outstanding load as finished (isInProgress
                // belongs to that load). Dedup: a listener instance queued
                // earlier and passed again as the direct callback must
                // receive exactly one onSuccess per delivery.
                val queued = loadingStates[contextKey]?.callbacks?.let { callbacks ->
                    val snapshot = callbacks.toList()
                    callbacks.clear()
                    snapshot
                }.orEmpty()
                queued.forEach { it.onSuccess(cached) }
                // Identity, not equals: a host listener with value semantics
                // (data class) must not suppress a distinct caller.
                if (callback != null && queued.none { it === callback }) {
                    callback.onSuccess(cached)
                }
                return@postIdentityAction
            }

        val loadingState = loadingStates[contextKey] ?: LoadingState()
        loadingStates[contextKey] = loadingState

        callback?.let {
            loadingState.callbacks.add(it)
        }

        if (!userStateProvider.isUserStable || loadingState.isInProgress) {
            return@postIdentityAction
        }

        loadingState.isInProgress = true
        loadingState.loadedConfig = null
        val generationAtStart = invalidationGeneration.get()
        val requestIdentity = captureRequestIdentity()

        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                postIdentityAction {
                    if (requestIdentity.isCurrentAndStable()) {
                        loadRemoteConfigFromService(
                            contextKey,
                            loadingState,
                            generationAtStart,
                            requestIdentity,
                        )
                    } else {
                        reissueSingleAfterUserChange(contextKey, loadingState)
                    }
                }
            }
        })
    }

    private fun loadRemoteConfigFromService(
        contextKey: String?,
        loadingState: LoadingState,
        generationAtStart: Int,
        requestIdentity: RemoteConfigRequestIdentity,
    ) {
        remoteConfigService.loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                postIdentityAction {
                    if (requestIdentity.isCurrentAndStable()) {
                        if (remoteConfig.source.contextKey == contextKey) {
                            handleRemoteConfigSuccess(
                                contextKey,
                                loadingState,
                                generationAtStart,
                                requestIdentity,
                                remoteConfig,
                            )
                        } else {
                            handleRemoteConfigError(
                                contextKey,
                                loadingState,
                                requestIdentity.cacheScope,
                                malformedRemoteConfigResponseError(),
                            )
                        }
                    } else {
                        reissueSingleAfterUserChange(contextKey, loadingState)
                    }
                }
            }

            override fun onError(error: QonversionError) {
                postIdentityAction {
                    if (requestIdentity.isCurrentAndStable()) {
                        if (error.code == QonversionErrorCode.RemoteConfigurationNotAvailable &&
                            requestIdentity.cacheScope != null
                        ) {
                            handleAuthoritativeRemoteConfigRemoval(
                                contextKey,
                                loadingState,
                                generationAtStart,
                                requestIdentity,
                                error,
                            )
                        } else {
                            handleRemoteConfigError(contextKey, loadingState, requestIdentity.cacheScope, error)
                        }
                    } else {
                        reissueSingleAfterUserChange(contextKey, loadingState)
                    }
                }
            }
        })
    }

    private fun handleAuthoritativeRemoteConfigRemoval(
        contextKey: String?,
        loadingState: LoadingState,
        generationAtStart: Int,
        requestIdentity: RemoteConfigRequestIdentity,
        error: QonversionError,
    ) {
        if (invalidationGeneration.get() != generationAtStart) {
            reissueSingleAfterUserChange(contextKey, loadingState)
            return
        }
        val cacheScope = requestIdentity.cacheScope ?: run {
            handleRemoteConfigError(contextKey, loadingState, null, error)
            return
        }
        persistentCache.remove(cacheScope, contextKey) { committed ->
            postIdentityAction {
                when {
                    !requestIdentity.isCurrentAndStable() ->
                        reissueSingleAfterUserChange(contextKey, loadingState)
                    invalidationGeneration.get() != generationAtStart ->
                        reissueSingleAfterUserChange(contextKey, loadingState)
                    committed -> handleRemoteConfigError(contextKey, loadingState, cacheScope, error)
                    else -> {
                        loadingState.retryBaseline = null
                        fireToCallbacks(contextKey) { onError(remoteConfigPersistenceError()) }
                    }
                }
            }
        }
    }

    private fun reissueSingleAfterUserChange(
        contextKey: String?,
        supersededState: LoadingState,
    ) {
        val waiters = supersededState.callbacks.toList()
        supersededState.callbacks.clear()
        supersededState.isInProgress = false
        enqueueIdentityAction {
            waiters.forEach { loadRemoteConfig(contextKey, it) }
        }
    }

    private fun handleRemoteConfigSuccess(
        contextKey: String?,
        loadingState: LoadingState,
        generationAtStart: Int,
        requestIdentity: RemoteConfigRequestIdentity,
        remoteConfig: QRemoteConfig,
    ) {
        loadingState.retryBaseline = null
        val currentGeneration = invalidationGeneration.get()
        if (currentGeneration != generationAtStart) {
            deliverOrReissueRemoteConfigSuccess(
                contextKey,
                loadingState,
                generationAtStart,
                remoteConfig,
            )
            return
        }

        val cacheScope = requestIdentity.cacheScope
        if (cacheScope == null) {
            deliverOrReissueRemoteConfigSuccess(
                contextKey,
                loadingState,
                generationAtStart,
                remoteConfig,
            )
            return
        }

        persistentCache.save(cacheScope, remoteConfig) { committed ->
            postIdentityAction {
                when {
                    !requestIdentity.isCurrentAndStable() ->
                        reissueSingleAfterUserChange(contextKey, loadingState)
                    invalidationGeneration.get() != generationAtStart ->
                        deliverOrReissueRemoteConfigSuccess(
                            contextKey,
                            loadingState,
                            generationAtStart,
                            remoteConfig,
                        )
                    committed -> deliverOrReissueRemoteConfigSuccess(
                        contextKey,
                        loadingState,
                        generationAtStart,
                        remoteConfig,
                    )
                    else -> handleRemoteConfigError(
                        contextKey,
                        loadingState,
                        cacheScope,
                        remoteConfigPersistenceError(),
                    )
                }
            }
        }
    }

    private fun deliverOrReissueRemoteConfigSuccess(
        contextKey: String?,
        loadingState: LoadingState,
        generationAtStart: Int,
        remoteConfig: QRemoteConfig,
    ) {
        val currentGeneration = invalidationGeneration.get()
        if (currentGeneration == generationAtStart) {
            deliveryOrigins[contextKey] = QRemoteConfigDeliveryOrigin.Network
            loadingState.loadedConfig = remoteConfig
            loadingState.generation = generationAtStart
            fireToCallbacks(contextKey) { onSuccess(remoteConfig) }
            return
        }

        // An invalidation superseded this evaluation. Re-issue only while the
        // loading state is still live; a user switch replaces the map and an
        // orphaned response must not start a request nobody awaits.
        val shouldReissue = loadingStates[contextKey] === loadingState &&
            loadingState.callbacks.isNotEmpty() &&
            loadingState.reissuedForGeneration != currentGeneration
        if (shouldReissue) {
            reissueRemoteConfig(contextKey, loadingState, currentGeneration, remoteConfig)
            return
        }

        deliveryOrigins[contextKey] = QRemoteConfigDeliveryOrigin.Network
        fireToCallbacks(contextKey) { onSuccess(remoteConfig) }
    }

    private fun reissueRemoteConfig(
        contextKey: String?,
        loadingState: LoadingState,
        currentGeneration: Int,
        baseline: QRemoteConfig,
    ) {
        loadingState.reissuedForGeneration = currentGeneration
        loadingState.isInProgress = false
        loadingState.retryBaseline = baseline
        val waiters = loadingState.callbacks.toList()
        loadingState.callbacks.clear()
        loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                waiters.forEach { it.onSuccess(remoteConfig) }
            }

            override fun onError(error: QonversionError) {
                // Safety net only: the retry stash normally resolves via
                // onSuccess when a transient request failure is eligible for
                // fallback. Authentication, other client errors and an
                // authoritative no-config response must remain errors.
                if (error.shouldFireRemoteConfigFallback) {
                    waiters.forEach { it.onSuccess(baseline) }
                } else {
                    waiters.forEach { it.onError(error) }
                }
            }
        })
    }

    private fun handleRemoteConfigError(
        contextKey: String?,
        loadingState: LoadingState,
        cacheScope: RemoteConfigCacheScope?,
        error: QonversionError,
    ) {
        val baseline = loadingState.retryBaseline
        loadingState.retryBaseline = null
        val canRecover = error.shouldFireRemoteConfigFallback
        val lastKnownGood = if (canRecover && cacheScope != null) {
            persistentCache.get(cacheScope, contextKey)
        } else {
            null
        }
        val bundledConfig = if (canRecover) bundledRemoteConfig(contextKey) else null

        // A real user-specific evaluation (even a superseded retry baseline)
        // outranks persisted LKG, which in turn outranks the static bundle.
        val result = baseline.takeIf { canRecover } ?: lastKnownGood ?: bundledConfig
        result?.let { config ->
            deliveryOrigins[contextKey] = when {
                baseline != null && canRecover -> QRemoteConfigDeliveryOrigin.RetryBaseline
                lastKnownGood != null -> QRemoteConfigDeliveryOrigin.PersistentLastKnownGood
                else -> QRemoteConfigDeliveryOrigin.BundledFallback
            }
            fireToCallbacks(contextKey) { onSuccess(config) }
        } ?: fireToCallbacks(contextKey) { onError(error) }
    }

    private fun bundledRemoteConfig(contextKey: String?): QRemoteConfig? =
        fallbackData?.remoteConfigList?.let { list ->
            if (contextKey == null) {
                list.remoteConfigForEmptyContextKey
            } else {
                list.remoteConfigForContextKey(contextKey)
            }
        }

    fun loadRemoteConfigList(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ) = loadRemoteConfigListNormalized(
        contextKeys.filter(String::isNotEmpty).distinct(),
        includeEmptyContextKey,
        callback,
    )

    private fun loadRemoteConfigListNormalized(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback,
    ) = postIdentityAction {
        val allKeys = if (includeEmptyContextKey) contextKeys + EmptyContextKey else contextKeys
        val currentGeneration = invalidationGeneration.get()
        val cachedConfigs = allKeys.map { key ->
            loadingStates[key]?.takeIf { it.generation == currentGeneration }?.loadedConfig
        }
        if (userStateProvider.isUserStable && cachedConfigs.all { it != null }) {
            allKeys.forEach { key ->
                deliveryOrigins[key] = QRemoteConfigDeliveryOrigin.MemoryCache
            }
            // Same as the single-key cache hit: flush pending properties so a
            // hit does not swallow them. Gated on stability (parity with iOS)
            // so the flush cannot POST mid-identify to a switching uid.
            if (userStateProvider.isUserStable) {
                userPropertiesManager.forceSendProperties()
            }
            callback.onSuccess(QRemoteConfigList(cachedConfigs.filterNotNull()))
            return@postIdentityAction
        }

        if (!userStateProvider.isUserStable) {
            listRequests.add(ListRequestData(callback, contextKeys, includeEmptyContextKey))
            return@postIdentityAction
        }

        val requestIdentity = captureRequestIdentity()
        val generationAtStart = invalidationGeneration.get()
        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                postIdentityAction {
                    if (requestIdentity.isCurrentAndStable()) {
                        remoteConfigService.loadRemoteConfigs(
                            contextKeys,
                            includeEmptyContextKey,
                            getRemoteConfigListCallbackWrapper(
                                contextKeys,
                                includeEmptyContextKey,
                                callback,
                                requestIdentity,
                                generationAtStart,
                            ),
                        )
                    } else {
                        reissueRemoteConfigListAfterUserChange(contextKeys, includeEmptyContextKey, callback)
                    }
                }
            }
        })
    }

    fun loadRemoteConfigList(callback: QonversionRemoteConfigListCallback) = postIdentityAction {
        if (!userStateProvider.isUserStable) {
            listRequests.add(ListRequestData(callback))
            return@postIdentityAction
        }

        val requestIdentity = captureRequestIdentity()
        val generationAtStart = invalidationGeneration.get()
        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                postIdentityAction {
                    if (requestIdentity.isCurrentAndStable()) {
                        remoteConfigService.loadRemoteConfigs(
                            getRemoteConfigListCallbackWrapper(
                                null,
                                true,
                                callback,
                                requestIdentity,
                                generationAtStart,
                            ),
                        )
                    } else {
                        reissueRemoteConfigListAfterUserChange(null, true, callback)
                    }
                }
            }
        })
    }

    // An attach/detach is addressed by experiment/configuration id, and the SDK does not
    // know which context key that entity serves — drop every cached config, not just the
    // empty-key one, or configs under named context keys stay stale until process restart.
    // The generation bump also stops in-flight loads from re-caching a pre-attach response.
    fun attachUserToExperiment(experimentId: String, groupId: String, callback: QonversionExperimentAttachCallback) =
        invalidateOnAnyThread {
            remoteConfigService.attachUserToExperiment(experimentId, groupId, callback)
        }

    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) =
        invalidateOnAnyThread {
            remoteConfigService.detachUserFromExperiment(experimentId, callback)
        }

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) = invalidateOnAnyThread {
        remoteConfigService.attachUserToRemoteConfiguration(remoteConfigurationId, callback)
    }

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) = invalidateOnAnyThread {
        remoteConfigService.detachUserFromRemoteConfiguration(remoteConfigurationId, callback)
    }

    // Shared invalidation shape for every seam: the generation is bumped
    // SYNCHRONOUSLY on the caller thread — a load issued right after any
    // invalidation, from any thread, must already see the cache as stale
    // (the atomic gives the happens-before the main-thread hop cannot) —
    // then the cached values are cleared and the action runs on main.
    private fun invalidateOnAnyThread(action: () -> Unit) {
        invalidationGeneration.incrementAndGet()
        postIdentityAction {
            loadingStates.values.forEach { it.loadedConfig = null }
            deliveryOrigins.clear()
            action()
        }
    }

    private fun getRemoteConfigListCallbackWrapper(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback,
        requestIdentity: RemoteConfigRequestIdentity,
        generationAtStart: Int,
    ): QonversionRemoteConfigListCallback {
        // Remembering loading states for the case of user change -
        // if it happens, we won't store remote configs for different user.
        val localLoadingStates = loadingStates
        return object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                postIdentityAction {
                    if (!requestIdentity.isCurrentAndStable()) {
                        reissueRemoteConfigListAfterUserChange(contextKeys, includeEmptyContextKey, callback)
                        return@postIdentityAction
                    }
                    if (!remoteConfigListMatchesRequest(contextKeys, includeEmptyContextKey, remoteConfigList)) {
                        val error = malformedRemoteConfigResponseError()
                        remoteConfigListFallback(
                            contextKeys,
                            includeEmptyContextKey,
                            requestIdentity.cacheScope,
                        )?.let(callback::onSuccess) ?: callback.onError(error)
                        return@postIdentityAction
                    }
                    handleRemoteConfigListSuccess(
                        contextKeys,
                        includeEmptyContextKey,
                        callback,
                        requestIdentity,
                        generationAtStart,
                        localLoadingStates,
                        remoteConfigList,
                    )
                }
            }

            override fun onError(error: QonversionError) {
                postIdentityAction {
                    when {
                        !requestIdentity.isCurrentAndStable() ->
                            reissueRemoteConfigListAfterUserChange(contextKeys, includeEmptyContextKey, callback)
                        !error.shouldFireRemoteConfigFallback -> callback.onError(error)
                        else -> remoteConfigListFallback(
                            contextKeys,
                            includeEmptyContextKey,
                            requestIdentity.cacheScope,
                        )?.let(callback::onSuccess) ?: callback.onError(error)
                    }
                }
            }
        }
    }

    private fun remoteConfigListMatchesRequest(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        remoteConfigList: QRemoteConfigList,
    ): Boolean {
        val returnedContextKeys = remoteConfigList.remoteConfigs.map { it.source.contextKey }
        val requestedContextKeys = contextKeys?.let { keys ->
            buildSet<String?> {
                addAll(keys)
                if (includeEmptyContextKey) add(null)
            }
        }
        return returnedContextKeys.size == returnedContextKeys.distinct().size &&
            (requestedContextKeys == null || returnedContextKeys.all(requestedContextKeys::contains))
    }

    private fun malformedRemoteConfigResponseError() = QonversionError(
        QonversionErrorCode.ResponseParsingFailed,
        "Remote Config response does not match the request",
    )

    private fun remoteConfigPersistenceError() = QonversionError(
        QonversionErrorCode.ResponseParsingFailed,
        "Remote Config could not be persisted as last known good",
    )

    private fun handleRemoteConfigListSuccess(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback,
        requestIdentity: RemoteConfigRequestIdentity,
        generationAtStart: Int,
        localLoadingStates: MutableMap<String?, LoadingState>,
        remoteConfigList: QRemoteConfigList,
    ) {
        if (invalidationGeneration.get() != generationAtStart) {
            // Preserve the legacy list contract: an already-valid response is
            // still delivered, but a superseded evaluation is never promoted
            // into either the in-memory cache or the persistent LKG.
            remoteConfigList.remoteConfigs.forEach { remoteConfig ->
                deliveryOrigins[remoteConfig.source.contextKey] = QRemoteConfigDeliveryOrigin.Network
            }
            callback.onSuccess(remoteConfigList)
            return
        }

        val cacheScope = requestIdentity.cacheScope
        if (cacheScope == null) {
            completeRemoteConfigListSuccess(
                callback,
                generationAtStart,
                localLoadingStates,
                remoteConfigList,
            )
            return
        }

        reconcilePersistentCache(
            contextKeys,
            includeEmptyContextKey,
            cacheScope,
            remoteConfigList.remoteConfigs,
        ) { committed ->
            postIdentityAction {
                when {
                    !requestIdentity.isCurrentAndStable() ->
                        reissueRemoteConfigListAfterUserChange(contextKeys, includeEmptyContextKey, callback)
                    invalidationGeneration.get() != generationAtStart ->
                        reissueRemoteConfigList(contextKeys, includeEmptyContextKey, callback)
                    committed -> completeRemoteConfigListSuccess(
                        callback,
                        generationAtStart,
                        localLoadingStates,
                        remoteConfigList,
                    )
                    else -> remoteConfigListFallback(
                        contextKeys,
                        includeEmptyContextKey,
                        cacheScope,
                    )?.let(callback::onSuccess) ?: callback.onError(remoteConfigPersistenceError())
                }
            }
        }
    }

    private fun completeRemoteConfigListSuccess(
        callback: QonversionRemoteConfigListCallback,
        generationAtStart: Int,
        localLoadingStates: MutableMap<String?, LoadingState>,
        remoteConfigList: QRemoteConfigList,
    ) {
        remoteConfigList.remoteConfigs.forEach { remoteConfig ->
            deliveryOrigins[remoteConfig.source.contextKey] = QRemoteConfigDeliveryOrigin.Network
        }
        remoteConfigList.remoteConfigs.forEach { remoteConfig ->
            val contextKey = remoteConfig.source.contextKey
            val loadingState = localLoadingStates[contextKey] ?: LoadingState()
            loadingState.loadedConfig = remoteConfig
            loadingState.generation = generationAtStart
            localLoadingStates[contextKey] = loadingState
        }

        callback.onSuccess(remoteConfigList)
    }

    private fun reconcilePersistentCache(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        cacheScope: RemoteConfigCacheScope,
        remoteConfigs: List<QRemoteConfig>,
        completion: (Boolean) -> Unit,
    ) {
        if (contextKeys == null) {
            persistentCache.replaceAll(cacheScope, remoteConfigs, completion)
            return
        }

        val requestedContextKeys = buildList<String?> {
            addAll(contextKeys)
            if (includeEmptyContextKey) add(null)
        }.toSet()
        persistentCache.replaceRequested(cacheScope, requestedContextKeys, remoteConfigs, completion)
    }

    private fun remoteConfigListFallback(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        cacheScope: RemoteConfigCacheScope?,
    ): QRemoteConfigList? {
        val persistedConfigs = cacheScope?.let { persistentCache.getAll(it).remoteConfigs }.orEmpty()
        val bundledConfigList = fallbackData?.remoteConfigList
        return if (persistedConfigs.isEmpty() && bundledConfigList == null) {
            null
        } else {
            val result = mergeFallbackConfigs(
                contextKeys,
                includeEmptyContextKey,
                persistedConfigs,
                bundledConfigList,
            )
            markFallbackOrigins(result, persistedConfigs)
            result
        }
    }

    private fun mergeFallbackConfigs(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        persistedConfigs: List<QRemoteConfig>,
        bundledConfigList: QRemoteConfigList?,
    ): QRemoteConfigList {
        val persistedByContext = persistedConfigs.associateBy { it.source.contextKey }
        val bundledByContext = bundledConfigList?.remoteConfigs.orEmpty().associateBy { it.source.contextKey }
        val desiredContextKeys = contextKeys?.let { keys ->
            buildList<String?> {
                addAll(keys)
                if (includeEmptyContextKey) add(null)
            }.distinct()
        } ?: (persistedByContext.keys + bundledByContext.keys)

        return QRemoteConfigList(desiredContextKeys.mapNotNull { key ->
            persistedByContext[key] ?: bundledByContext[key]
        })
    }

    private fun markFallbackOrigins(
        remoteConfigList: QRemoteConfigList,
        persistedConfigs: List<QRemoteConfig>,
    ) {
        val persistedContextKeys = persistedConfigs.map { it.source.contextKey }.toSet()
        remoteConfigList.remoteConfigs.forEach { remoteConfig ->
            deliveryOrigins[remoteConfig.source.contextKey] =
                if (remoteConfig.source.contextKey in persistedContextKeys) {
                    QRemoteConfigDeliveryOrigin.PersistentLastKnownGood
                } else {
                    QRemoteConfigDeliveryOrigin.BundledFallback
                }
        }
    }

    private fun reissueRemoteConfigList(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback,
    ) {
        contextKeys?.let {
            loadRemoteConfigList(it, includeEmptyContextKey, callback)
        } ?: loadRemoteConfigList(callback)
    }

    private fun reissueRemoteConfigListAfterUserChange(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback,
    ) = enqueueIdentityAction {
        reissueRemoteConfigList(contextKeys, includeEmptyContextKey, callback)
    }

    private fun fireToCallbacks(contextKey: String?, action: QonversionRemoteConfigCallback.() -> Unit) {
        loadingStates[contextKey]?.let { loadingState ->
            loadingState.isInProgress = false
            val callbacks = loadingState.callbacks.toList()
            loadingState.callbacks.clear()
            callbacks.forEach { it.action() }
        }
    }

    // Confines every access to the mutable loading/list state to the main thread, which
    // removes the ConcurrentModificationException at its source without locks. Runs the
    // action inline when already on the main thread, preserving the synchronous
    // cached-config fast paths in loadRemoteConfig/loadRemoteConfigList.
    private fun postToMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun postIdentityAction(action: () -> Unit) = postToMainThread {
        synchronized(identityTransitionLock) {
            resetIdentityStateIfNeeded()
            action()
        }
    }

    private fun enqueueIdentityAction(action: () -> Unit) {
        mainHandler.post {
            synchronized(identityTransitionLock) {
                resetIdentityStateIfNeeded()
                action()
            }
        }
    }

    private fun captureRequestIdentity() = RemoteConfigRequestIdentity(
        userGeneration = userGeneration.get(),
        cacheScope = persistentCache.currentScope(),
    )

    private fun RemoteConfigRequestIdentity.isCurrentAndStable(): Boolean =
        this@QRemoteConfigManager.userGeneration.get() == this.userGeneration &&
            persistentCache.currentScope() == cacheScope &&
            userStateProvider.isUserStable
}
