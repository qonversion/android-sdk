package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import javax.inject.Inject

internal class QRemoteConfigManager @Inject constructor(
    private val remoteConfigService: QRemoteConfigService,
    private val userInfoService: QUserInfoService,
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

    fun userChangingRequestsFailedWithError(error: QonversionError) {
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
        val userId = userInfoService.obtainUserID()
        remoteConfigService.attachUserToExperiment(experimentId, groupId, userId, callback)
    }

    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        currentRemoteConfig = null
        val userId = userInfoService.obtainUserID()
        remoteConfigService.detachUserFromExperiment(experimentId, userId, callback)
    }

    private fun fireToCallbacks(action: QonversionRemoteConfigCallback.() -> Unit) {
        val callbacks = remoteConfigCallbacks.toList()
        callbacks.forEach { it.action() }
        remoteConfigCallbacks.clear()
    }
}
