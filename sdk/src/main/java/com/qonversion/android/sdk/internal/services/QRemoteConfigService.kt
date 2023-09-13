package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import javax.inject.Inject

internal class QRemoteConfigService @Inject constructor(
    private val repository: QRepository
) {
    fun loadRemoteConfig(userId: String, callback: QonversionRemoteConfigCallback) {
        repository.remoteConfig(userId, callback)
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
}
