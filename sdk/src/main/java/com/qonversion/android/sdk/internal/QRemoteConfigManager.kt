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
    lateinit var userStateProvider: UserStateProvider
    private var currentRemoteConfig: QRemoteConfig? = null
    private var remoteConfigCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
    private var isRequestInProgress: Boolean = false

    fun handlePendingRequests() {
        if (remoteConfigCallbacks.isNotEmpty()) {
            loadRemoteConfig(null)
        }
    }

    fun userChangingRequestFailedWithError(error: QonversionError) {
        fireToCallbacks { onError(error) }
    }

    fun onUserUpdate() {
        currentRemoteConfig = null
    }

    fun loadRemoteConfig(callback: QonversionRemoteConfigCallback?) {
        currentRemoteConfig?.takeIf { userStateProvider.isUserStable }?.let {
            callback?.onSuccess(it)
            return
        }

        callback?.let {
            remoteConfigCallbacks.add(it)
        }

        if (!userStateProvider.isUserStable || isRequestInProgress) {
            return
        }

        isRequestInProgress = true
        currentRemoteConfig = null
        remoteConfigService.loadRemoteConfig(internalConfig.uid, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                isRequestInProgress = false
                currentRemoteConfig = remoteConfig
                fireToCallbacks { onSuccess(remoteConfig) }
            }

            override fun onError(error: QonversionError) {
                isRequestInProgress = false
                fireToCallbacks { onError(error) }
            }
        })
    }

    fun attachUserToExperiment(experimentId: String, groupId: String, callback: QonversionExperimentAttachCallback) {
        currentRemoteConfig = null
        remoteConfigService.attachUserToExperiment(experimentId, groupId, internalConfig.uid, callback)
    }

    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        currentRemoteConfig = null
        remoteConfigService.detachUserFromExperiment(experimentId, internalConfig.uid, callback)
    }

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        currentRemoteConfig = null
        remoteConfigService.attachUserToRemoteConfiguration(remoteConfigurationId, internalConfig.uid, callback)
    }

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        currentRemoteConfig = null
        remoteConfigService.detachUserFromRemoteConfiguration(remoteConfigurationId, internalConfig.uid, callback)
    }

    private fun fireToCallbacks(action: QonversionRemoteConfigCallback.() -> Unit) {
        val callbacks = remoteConfigCallbacks.toList()
        callbacks.forEach { it.action() }
        remoteConfigCallbacks.clear()
    }
}
