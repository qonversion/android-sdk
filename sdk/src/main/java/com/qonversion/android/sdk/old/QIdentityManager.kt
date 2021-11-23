package com.qonversion.android.sdk.old

import com.qonversion.android.sdk.old.services.QUserInfoService
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
        val currentUserID = userInfoService.obtainUserID()
        repository.identify(userID, currentUserID,
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

    fun logoutIfNeeded(): Boolean {
        return userInfoService.logoutIfNeeded()
    }
}
