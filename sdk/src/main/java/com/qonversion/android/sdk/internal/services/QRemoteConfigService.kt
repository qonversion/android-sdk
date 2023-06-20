package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.QonversionRepository
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import javax.inject.Inject

internal class QRemoteConfigService @Inject constructor(
    private val repository: QonversionRepository
) {
    fun loadRemoteConfig(userId: String, callback: QonversionRemoteConfigCallback) {
        repository.remoteConfig(userId, callback)
    }
}