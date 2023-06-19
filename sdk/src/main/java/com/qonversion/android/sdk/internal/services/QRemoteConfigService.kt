package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.QonversionRepository
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import javax.inject.Inject

class QRemoteConfigService @Inject internal constructor(
    private val repository: QonversionRepository
){
    fun loadRemoteConfig(userId: String, callback: QonversionRemoteConfigCallback) {
        repository.remoteConfig(userId, callback)
    }

    fun attachUserToExperiment(experimentId: String, groupId: String, userId: String, callback: QonversionExperimentAttachCallback) {
        repository.attachUserToExperiment(experimentId, groupId, userId, callback)
    }

    fun detachUserToExperiment(experimentId: String, userId: String, callback: QonversionExperimentAttachCallback) {
        repository.detachUserToExperiment(experimentId, userId, callback)
    }
}