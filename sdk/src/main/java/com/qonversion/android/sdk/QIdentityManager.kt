package com.qonversion.android.sdk

import com.qonversion.android.sdk.services.QUserInfoService
import javax.inject.Inject
import java.net.HttpURLConnection

interface IdentityManagerCallback {
    fun onSuccess(identityID: String)
    fun onError(error: QonversionError, responseCode: Int?)
}

class QIdentityManager @Inject constructor(
    private val repository: QonversionRepository,
    private val userInfoService: QUserInfoService
) {
    fun identify(userID: String, callback: IdentityManagerCallback) {
        obtainIdentity(userID, object : IdentityManagerCallback {
            override fun onSuccess(identityID: String) {
                if (identityID.isNotEmpty()) {
                    userInfoService.storeIdentity(identityID)
                }

                callback.onSuccess(identityID)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    createIdentity(userID, callback)
                }
            }
        })
    }

    fun createIdentity(userID: String, callback: IdentityManagerCallback) {
        val currentUserID = userInfoService.obtainUserID()
        repository.createIdentity(userID, currentUserID,
            onSuccess = { resultUserID ->
                if (resultUserID.isNotEmpty()) {
                    userInfoService.storeIdentity(resultUserID)
                }

                callback.onSuccess(resultUserID)
            },
            onError = { error, code ->
                callback.onError(error, code)
            })
    }

    fun obtainIdentity(userID: String, callback: IdentityManagerCallback) {
        repository.obtainIdentity(userID,
            onSuccess = { resultUserID ->
                if (resultUserID.isNotEmpty()) {
                    userInfoService.storeIdentity(resultUserID)
                }

                callback.onSuccess(resultUserID)
            },
            onError = { error, code ->
                callback.onError(error, code)
            })
    }

    fun logoutIfNeeded(): Boolean {
        return userInfoService.logoutIfNeeded()
    }
}
