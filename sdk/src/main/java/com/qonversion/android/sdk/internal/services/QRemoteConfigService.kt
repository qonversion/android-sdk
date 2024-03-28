package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import javax.inject.Inject

internal class QRemoteConfigService @Inject constructor(
    private val repository: QRepository
) {
    fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback) {
        repository.remoteConfig(contextKey, callback)
    }

    fun loadRemoteConfigs(callback: QonversionRemoteConfigListCallback) {
        repository.remoteConfigList(callback)
    }

    fun loadRemoteConfigs(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ) {
        repository.remoteConfigList(contextKeys, includeEmptyContextKey, callback)
    }

    fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        repository.attachUserToExperiment(experimentId, groupId, callback)
    }

    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        repository.detachUserFromExperiment(experimentId, callback)
    }

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        repository.attachUserToRemoteConfiguration(remoteConfigurationId, callback)
    }

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        repository.detachUserFromRemoteConfiguration(remoteConfigurationId, callback)
    }
}
