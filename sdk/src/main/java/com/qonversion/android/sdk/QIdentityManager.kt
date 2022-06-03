package com.qonversion.android.sdk

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.services.QUserInfoService
import javax.inject.Inject
import java.net.HttpURLConnection

interface IdentityManagerCallback {
    fun onSuccess(qonversionUserId: String)
    fun onError(error: QonversionError, responseCode: Int?)
}

internal class QIdentityManager @Inject constructor(
    private val repository: QonversionRepository,
    private val userInfoService: QUserInfoService
) {
    val currentCustomUserId: String? get() = userInfoService.getCustomUserId()

    fun identify(userID: String, callback: IdentityManagerCallback) {
        obtainIdentity(userID, object : IdentityManagerCallback {
            override fun onSuccess(qonversionUserId: String) {
                callback.onSuccess(qonversionUserId)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    createIdentity(userID, callback)
                } else {
                    callback.onError(error, responseCode)
                }
            }
        })
    }

    fun createIdentity(userID: String, callback: IdentityManagerCallback) {
        val qonversionUserID = userInfoService.obtainUserID()
        repository.createIdentity(qonversionUserID, userID,
            onSuccess = { newQonversionUserId -> handleIdentity(callback, newQonversionUserId, userID) },
            onError = { error, code ->
                callback.onError(error, code)
            })
    }

    fun obtainIdentity(userID: String, callback: IdentityManagerCallback) {
        repository.obtainIdentity(userID,
            onSuccess = { qonversionUserId -> handleIdentity(callback, qonversionUserId, userID) },
            onError = { error, code ->
                callback.onError(error, code)
            })
    }

    @VisibleForTesting
    fun handleIdentity(callback: IdentityManagerCallback, qonversionUserId: String, customUserId: String) {
        userInfoService.storeCustomUserId(customUserId)
        if (qonversionUserId.isNotEmpty()) {
            userInfoService.storeQonversionUserId(qonversionUserId)
        }

        callback.onSuccess(qonversionUserId)
    }

    fun logoutIfNeeded(): Boolean {
        return userInfoService.logoutIfNeeded()
    }
}
