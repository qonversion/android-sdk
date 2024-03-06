package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import javax.inject.Inject

internal class QRemoteConfigService @Inject constructor(
    private val repository: QRepository
) {
    fun loadRemoteConfig(
        userId: String,
        contextKey: String?,
        callback: QonversionRemoteConfigCallback
    ) {
        repository.remoteConfig(userId, contextKey, callback)
    }

    fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        userId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        repository.attachUserToExperiment(experimentId, groupId, userId, callback)
    }

    fun detachUserFromExperiment(experimentId: String, userId: String, callback: QonversionExperimentAttachCallback) {
        repository.detachUserFromExperiment(experimentId, userId, callback)
    }

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        userId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        repository.attachUserToRemoteConfiguration(remoteConfigurationId, userId, callback)
    }

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        userId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        repository.detachUserFromRemoteConfiguration(remoteConfigurationId, userId, callback)
    }
}
