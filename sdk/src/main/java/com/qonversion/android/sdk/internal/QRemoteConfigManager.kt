package com.qonversion.android.sdk.internal

import android.os.Handler
import android.os.Looper
import com.qonversion.android.sdk.dto.QFallbackObject
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.listeners.QonversionEmptyCallback
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import javax.inject.Inject

private val EmptyContextKey: String? = null

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
        var isInProgress: Boolean = false
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
        loadingStates.keys.forEach {
            fireToCallbacks(it) { onError(error) }
        }
    }

    fun onUserUpdate() = postToMainThread {
        loadingStates = mutableMapOf()
    }

    fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback?) = postToMainThread {
        loadingStates[contextKey]
            ?.loadedConfig
            ?.takeIf { userStateProvider.isUserStable }
            ?.let {
                callback?.onSuccess(it)
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

        userPropertiesManager.forceSendProperties(object : QonversionEmptyCallback {
            override fun onComplete() {
                remoteConfigService.loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
                    override fun onSuccess(remoteConfig: QRemoteConfig) {
                        loadingState.loadedConfig = remoteConfig
                        fireToCallbacks(contextKey) { onSuccess(remoteConfig) }
                    }

                    override fun onError(error: QonversionError) {
                        if (!error.shouldFireFallback) {
                            fireToCallbacks(contextKey) { onError(error) }
                            return
                        }

                        val baseRemoteConfigList = fallbackData?.remoteConfigList ?: run {
                            fireToCallbacks(contextKey) { onError(error) }
                            return@onError
                        }

                        val remoteConfig = if (contextKey == null) {
                            baseRemoteConfigList.remoteConfigForEmptyContextKey
                        } else {
                            baseRemoteConfigList.remoteConfigForContextKey(contextKey)
                        }

                        remoteConfig?.let {
                            onSuccess(it)
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
        if (allKeys.all { loadingStates[it]?.loadedConfig != null }) {
            val configs = allKeys.mapNotNull { loadingStates[it]?.loadedConfig }
            callback.onSuccess(QRemoteConfigList(configs))
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

    fun attachUserToExperiment(experimentId: String, groupId: String, callback: QonversionExperimentAttachCallback) = postToMainThread {
        loadingStates[EmptyContextKey]?.loadedConfig = null
        remoteConfigService.attachUserToExperiment(experimentId, groupId, callback)
    }

    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) = postToMainThread {
        loadingStates[EmptyContextKey]?.loadedConfig = null
        remoteConfigService.detachUserFromExperiment(experimentId, callback)
    }

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) = postToMainThread {
        loadingStates[EmptyContextKey]?.loadedConfig = null
        remoteConfigService.attachUserToRemoteConfiguration(remoteConfigurationId, callback)
    }

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) = postToMainThread {
        loadingStates[EmptyContextKey]?.loadedConfig = null
        remoteConfigService.detachUserFromRemoteConfiguration(remoteConfigurationId, callback)
    }

    private fun getRemoteConfigListCallbackWrapper(
        contextKeys: List<String>?,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ): QonversionRemoteConfigListCallback {
        // Remembering loading states for the case of user change -
        // if it happens, we won't store remote configs for different user.
        val localLoadingStates = loadingStates
        return object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                remoteConfigList.remoteConfigs.forEach { remoteConfig ->
                    val contextKey = remoteConfig.source.contextKey
                    val loadingState = localLoadingStates[contextKey] ?: LoadingState()
                    loadingState.loadedConfig = remoteConfig
                    localLoadingStates[contextKey] = loadingState
                }

                callback.onSuccess(remoteConfigList)
            }

            override fun onError(error: QonversionError) {
                if (!error.shouldFireFallback) {
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

                onSuccess(remoteConfigList)
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
