package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import javax.inject.Inject
import kotlin.math.exp

class QRemoteConfigManager @Inject internal constructor(
    private val remoteConfigService: QRemoteConfigService,
    private val userInfoService: QUserInfoService
) {
    private var currentRemoteConfig: QRemoteConfig? = null
    private var isLaunchFinished: Boolean = false
    private var remoteConfigCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
    private var isRequestInProgress: Boolean = false

    fun launchFinished(finished: Boolean) {
        isLaunchFinished = finished

        if (finished && remoteConfigCallbacks.isNotEmpty() && !isRequestInProgress) {
            loadRemoteConfig(null)
        }
    }

    fun loadRemoteConfig(callback: QonversionRemoteConfigCallback?) {
        currentRemoteConfig?.let {
            callback?.onSuccess(it)
            return
        }

        if (!isLaunchFinished || isRequestInProgress) {
            callback?.let {
                remoteConfigCallbacks.add(it)
                return
            }
        }

        isRequestInProgress = true
        val userId = userInfoService.obtainUserID()
        remoteConfigService.loadRemoteConfig(userId, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                isRequestInProgress = false
                currentRemoteConfig = remoteConfig
                executeRemoteConfigCompletions(remoteConfig, null)
            }

            override fun onError(error: QonversionError) {
                isRequestInProgress = false
                executeRemoteConfigCompletions(null, error)
            }

        })
    }

    fun attachUserToExperiment(experimentId: String, groupId: String, callback: QonversionExperimentAttachCallback) {
        val userId = userInfoService.obtainUserID()
        remoteConfigService.attachUserToExperiment(experimentId, groupId, userId, callback)
    }

    fun detachUserToExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        val userId = userInfoService.obtainUserID()
        remoteConfigService.detachUserToExperiment(experimentId, userId, callback)
    }

    fun executeRemoteConfigCompletions(config: QRemoteConfig?, error: QonversionError?) {
        val callbacks = remoteConfigCallbacks.toList()
        remoteConfigCallbacks.clear()

        config?.let {
            callbacks.forEach { it.onSuccess(config)}
        }

        error?.let {
            callbacks.forEach { it.onError(error) }
        }
    }
}
