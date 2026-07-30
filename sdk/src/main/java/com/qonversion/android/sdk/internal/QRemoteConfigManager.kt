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
import com.qonversion.android.sdk.listeners.QonversionEmptyCallback
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private val EmptyContextKey: String? = null

// Rate-limit tolerance is scoped to remote configs deliberately: the other
// shouldFireFallback consumer (the entitlements path) keeps surfacing
// ApiRateLimitExceeded unchanged. A locally short-circuited RC request is
// exactly the case the bundled payload exists for — and since fallbacks are
// no longer cached, offline repeat calls hit the limiter instead of the old
// cached-fallback fast path.
private val QonversionError.shouldFireRemoteConfigFallback
    get(): Boolean = shouldFireFallback || code == QonversionErrorCode.ApiRateLimitExceeded

internal class QRemoteConfigManager @Inject constructor(
    private val remoteConfigService: QRemoteConfigService,
    private val fallbacksService: QFallbacksService
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
    private val listRequests = mutableListOf<ListRequestData>()
    lateinit var userPropertiesManager: QUserPropertiesManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Bumped on every cache invalidation (attach/detach, user change, explicit
    // invalidateRemoteConfigsCache). Loads capture it when they start and skip
    // the cache write if it moved — an in-flight response evaluated before the
    // invalidating event must not be re-cached as fresh. Atomic rather than
    // main-confined: the explicit invalidation bumps it synchronously on the
    // caller thread, so the cached fast paths reject stale values immediately
    // instead of waiting for the posted main-thread hop to drain.
    private val invalidationGeneration = AtomicInteger(0)

    fun handlePendingRequests() = postToMainThread {
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

    fun userChangingRequestFailedWithError(error: QonversionError) = postToMainThread {
        // Snapshot the keys: fireToCallbacks runs user callbacks, and a callback that
        // re-enters loadRemoteConfig with a new key runs inline (already on the main thread)
        // and registers that key in loadingStates. Iterating a copy keeps that re-entrant
        // structural add from mutating the map mid-iteration. Main-thread confinement does
        // not help here - the reentrancy is within a single thread.
        loadingStates.keys.toList().forEach {
            fireToCallbacks(it) { onError(error) }
        }
    }

    // Public cache invalidation seam (DEV-1236 B4): marks every cached config
    // stale so the next load fetches a fresh evaluation. Non-destructive —
    // loading states and pending callbacks survive, and the generation bump
    // stops in-flight loads from re-caching a superseded response.
    fun invalidateRemoteConfigsCache() = invalidateOnAnyThread {}

    fun onUserUpdate() {
        // Bump synchronously (see invalidateOnAnyThread) — the destructive
        // map replacement still happens on main.
        invalidationGeneration.incrementAndGet()
        postToMainThread {
            loadingStates = mutableMapOf()
        }
    }

    // The explicit Unit is required: the re-issue path recurses into this
    // function, and an inferred expression-body type would depend on itself.
    fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback?): Unit = postToMainThread {
        loadingStates[contextKey]
            ?.takeIf { it.generation == invalidationGeneration.get() }
            ?.loadedConfig
            ?.takeIf { userStateProvider.isUserStable }
            ?.let { cached ->
                // The cached config is served as is, but properties set right
                // before this call must still reach the server (parity with
                // iOS) - a cache hit must not swallow the flush.
                userPropertiesManager.forceSendProperties()
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
                if (callback != null && callback !in queued) {
                    callback.onSuccess(cached)
                }
                return@postToMainThread
            }

        val loadingState = loadingStates[contextKey] ?: LoadingState()
        loadingStates[contextKey] = loadingState

        callback?.let {
            loadingState.callbacks.add(it)
        }

        if (!userStateProvider.isUserStable || loadingState.isInProgress) {
            return@postToMainThread
        }

        loadingState.isInProgress = true
        loadingState.loadedConfig = null
        val generationAtStart = invalidationGeneration.get()

        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                remoteConfigService.loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
                    override fun onSuccess(remoteConfig: QRemoteConfig) {
                        // A successful (or delivered-as-is) response always
                        // supersedes any baseline stashed by an earlier retry.
                        loadingState.retryBaseline = null
                        val currentGeneration = invalidationGeneration.get()
                        if (currentGeneration == generationAtStart) {
                            loadingState.loadedConfig = remoteConfig
                            loadingState.generation = generationAtStart
                            fireToCallbacks(contextKey) { onSuccess(remoteConfig) }
                            return
                        }

                        // The cache was invalidated while this load was in
                        // flight, so this evaluation is already superseded.
                        // Re-issue the load once per generation so the waiting
                        // callbacks receive a fresh evaluation instead of the
                        // stale one. The state must still be live: a user
                        // switch replaces the map, and an orphaned state must
                        // not fire a request nobody awaits. The waiters are
                        // snapshotted and carried through the retry with the
                        // superseded (but valid) evaluation as a baseline — a
                        // failed retry degrades to the baseline instead of
                        // surfacing an error where the caller previously got
                        // a success. The generation cap is defense-in-depth:
                        // the retry is bounded primarily by the per-key
                        // isInProgress serialisation (one load, hence one
                        // superseded response, per generation).
                        if (loadingStates[contextKey] === loadingState &&
                            loadingState.callbacks.isNotEmpty() &&
                            loadingState.reissuedForGeneration != currentGeneration
                        ) {
                            loadingState.reissuedForGeneration = currentGeneration
                            loadingState.isInProgress = false
                            // The stash makes the never-worse guarantee
                            // uniform: the retry's failure handlers prefer it
                            // over both the error and the bundled fallback,
                            // reaching late joiners queued during the retry.
                            loadingState.retryBaseline = remoteConfig
                            val waiters = loadingState.callbacks.toList()
                            loadingState.callbacks.clear()
                            val baseline = remoteConfig
                            loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
                                override fun onSuccess(remoteConfig: QRemoteConfig) {
                                    waiters.forEach { it.onSuccess(remoteConfig) }
                                }

                                override fun onError(error: QonversionError) {
                                    // Safety net only: with the stash in place
                                    // the retry resolves via onSuccess; this
                                    // branch survives for exotic interleavings.
                                    waiters.forEach { it.onSuccess(baseline) }
                                }
                            })
                            return
                        }
                        fireToCallbacks(contextKey) { onSuccess(remoteConfig) }
                    }

                    override fun onError(error: QonversionError) {
                        val baseline = loadingState.retryBaseline
                        loadingState.retryBaseline = null
                        // The fallback is a bundled last-resort payload, not a
                        // fresh targeting evaluation — deliver it without
                        // caching so the next call retries the network instead
                        // of pinning the fallback until the next invalidation.
                        val bundledConfig = if (error.shouldFireRemoteConfigFallback) {
                            fallbackData?.remoteConfigList?.let { list ->
                                if (contextKey == null) {
                                    list.remoteConfigForEmptyContextKey
                                } else {
                                    list.remoteConfigForContextKey(contextKey)
                                }
                            }
                        } else {
                            null
                        }

                        // A failed retry of a superseded load degrades to the
                        // baseline — a real user-specific evaluation seconds
                        // old — for everyone, including callers who joined
                        // during the retry window. It outranks both the error
                        // and the static bundled payload.
                        val result = baseline ?: bundledConfig
                        result?.let { config ->
                            fireToCallbacks(contextKey) { onSuccess(config) }
                        } ?: fireToCallbacks(contextKey) { onError(error) }
                    }
                })
            }
        })
    }

    fun loadRemoteConfigList(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ) = postToMainThread {
        val allKeys = if (includeEmptyContextKey) contextKeys + EmptyContextKey else contextKeys
        val currentGeneration = invalidationGeneration.get()
        val cachedConfigs = allKeys.map { key ->
            loadingStates[key]?.takeIf { it.generation == currentGeneration }?.loadedConfig
        }
        if (cachedConfigs.all { it != null }) {
            // Same as the single-key cache hit: flush pending properties so a
            // hit does not swallow them. Gated on stability (parity with iOS)
            // so the flush cannot POST mid-identify to a switching uid.
            if (userStateProvider.isUserStable) {
                userPropertiesManager.forceSendProperties()
            }
            callback.onSuccess(QRemoteConfigList(cachedConfigs.filterNotNull()))
            return@postToMainThread
        }

        if (!userStateProvider.isUserStable) {
            listRequests.add(ListRequestData(callback, contextKeys, includeEmptyContextKey))
            return@postToMainThread
        }

        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                remoteConfigService.loadRemoteConfigs(
                    contextKeys,
                    includeEmptyContextKey,
                    getRemoteConfigListCallbackWrapper(contextKeys, includeEmptyContextKey, callback),
                )
            }
        })
    }

    fun loadRemoteConfigList(callback: QonversionRemoteConfigListCallback) = postToMainThread {
        if (!userStateProvider.isUserStable) {
            listRequests.add(ListRequestData(callback))
            return@postToMainThread
        }

        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                remoteConfigService.loadRemoteConfigs(getRemoteConfigListCallbackWrapper(null, true, callback))
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
        postToMainThread {
            loadingStates.values.forEach { it.loadedConfig = null }
            action()
        }
    }

    private fun getRemoteConfigListCallbackWrapper(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ): QonversionRemoteConfigListCallback {
        // Remembering loading states for the case of user change -
        // if it happens, we won't store remote configs for different user.
        val localLoadingStates = loadingStates
        val generationAtStart = invalidationGeneration.get()
        return object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                if (invalidationGeneration.get() == generationAtStart) {
                    remoteConfigList.remoteConfigs.forEach { remoteConfig ->
                        val contextKey = remoteConfig.source.contextKey
                        val loadingState = localLoadingStates[contextKey] ?: LoadingState()
                        loadingState.loadedConfig = remoteConfig
                        loadingState.generation = generationAtStart
                        localLoadingStates[contextKey] = loadingState
                    }
                }

                callback.onSuccess(remoteConfigList)
            }

            override fun onError(error: QonversionError) {
                if (!error.shouldFireRemoteConfigFallback) {
                    callback.onError(error)
                    return
                }

                val baseRemoteConfigList = fallbackData?.remoteConfigList ?: run {
                    callback.onError(error)
                    return@onError
                }

                val remoteConfigList = if (contextKeys == null) {
                    baseRemoteConfigList.copy()
                } else {
                    val remoteConfigs = baseRemoteConfigList.remoteConfigs.filter { contextKeys.contains(it.source.contextKey) }.toMutableList()
                    if (includeEmptyContextKey) {
                        baseRemoteConfigList.remoteConfigs.find { it.source.contextKey?.isEmpty() == true }?.let {
                            remoteConfigs.add(it)
                        }
                    }
                    QRemoteConfigList(remoteConfigs.toList())
                }

                // Bundled fallback, not a fresh targeting evaluation — deliver
                // without caching (see the single-key path), so the next call
                // retries the network.
                callback.onSuccess(remoteConfigList)
            }
        }
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
}
