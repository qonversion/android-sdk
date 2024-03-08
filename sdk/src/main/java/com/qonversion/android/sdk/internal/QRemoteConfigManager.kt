package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import javax.inject.Inject

internal class QRemoteConfigManager @Inject constructor(
    private val remoteConfigService: QRemoteConfigService,
    private val internalConfig: InternalConfig
) {
    internal class LoadingState(
        var loadedConfig: QRemoteConfig? = null,
        val callbacks: MutableList<QonversionRemoteConfigCallback> = mutableListOf(),
        var isInProgress: Boolean = false
    )

    lateinit var userStateProvider: UserStateProvider
    private var loadingStates = mutableMapOf<String?, LoadingState>()

    fun handlePendingRequests() {
        loadingStates.filter { it.value.callbacks.isNotEmpty() }
            .keys.forEach { contextKey -> loadRemoteConfig(contextKey, null) }
    }

    fun userChangingRequestFailedWithError(error: QonversionError) {
        loadingStates.keys.forEach {
            fireToCallbacks(it) { onError(error) }
        }
    }

    fun onUserUpdate() {
        loadingStates = mutableMapOf()
    }

    fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback?) {
        loadingStates[contextKey]
            ?.loadedConfig
            ?.takeIf { userStateProvider.isUserStable }
            ?.let {
                callback?.onSuccess(it)
                return
            }

        val loadingState = loadingStates[contextKey] ?: LoadingState()
        loadingStates[contextKey] = loadingState

        callback?.let {
            loadingState.callbacks.add(it)
        }

        if (!userStateProvider.isUserStable || loadingState.isInProgress) {
            return
        }

        loadingState.isInProgress = true
        loadingState.loadedConfig = null
        remoteConfigService.loadRemoteConfig(internalConfig.uid, contextKey, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                loadingState.loadedConfig = remoteConfig
                fireToCallbacks(contextKey) { onSuccess(remoteConfig) }
            }

            override fun onError(error: QonversionError) {
                fireToCallbacks(contextKey) { onError(error) }
            }
        })
    }

    fun attachUserToExperiment(experimentId: String, groupId: String, callback: QonversionExperimentAttachCallback) {
        loadingStates[null]?.loadedConfig = null
        remoteConfigService.attachUserToExperiment(experimentId, groupId, internalConfig.uid, callback)
    }

    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        loadingStates[null]?.loadedConfig = null
        remoteConfigService.detachUserFromExperiment(experimentId, internalConfig.uid, callback)
    }

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        loadingStates[null]?.loadedConfig = null
        remoteConfigService.attachUserToRemoteConfiguration(remoteConfigurationId, internalConfig.uid, callback)
    }

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        loadingStates[null]?.loadedConfig = null
        remoteConfigService.detachUserFromRemoteConfiguration(remoteConfigurationId, internalConfig.uid, callback)
    }

    private fun fireToCallbacks(contextKey: String?, action: QonversionRemoteConfigCallback.() -> Unit) {
        loadingStates[contextKey]?.let { loadingState ->
            loadingState.isInProgress = false
            val callbacks = loadingState.callbacks.toList()
            loadingState.callbacks.clear()
            callbacks.forEach { it.action() }
        }
    }
}
