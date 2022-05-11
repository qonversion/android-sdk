package com.qonversion.android.sdk

import com.qonversion.android.sdk.services.QUserInfoService
import javax.inject.Inject

interface IdentityManagerCallback {
    fun onSuccess(identityID: String)
    fun onError(error: QonversionError)
}

class QIdentityManager @Inject constructor(
    private val repository: QonversionRepository,
    private val userInfoService: QUserInfoService
) {
    fun identify(userID: String, callback: IdentityManagerCallback) {
        obtainIdentity(userID, object: IdentityManagerCallback {
            override fun onSuccess(identityID: String) {
                if (identityID.isNotEmpty()) {
                    userInfoService.storeIdentity(identityID)
                }

                callback.onSuccess(identityID)
            }

            override fun onError(error: QonversionError) {
                if (error.code.equals(404)) {
                    createIdentity(userID, callback);
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
            onError = {
                callback.onError(it)
            })
    }

    fun obtainIdentity(userID: String, callback: IdentityManagerCallback) {
        val currentUserID = userInfoService.obtainUserID()
        repository.identity(userID,
            onSuccess = { resultUserID ->
                if (resultUserID.isNotEmpty()) {
                    userInfoService.storeIdentity(resultUserID)
                }

                callback.onSuccess(resultUserID)
            },
            onError = {
                callback.onError(it)
            })
    }

    fun logout(): String {
        return userInfoService.logout()
    }
}
