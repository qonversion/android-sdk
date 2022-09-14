package com.qonversion.android.sdk

import com.qonversion.android.sdk.services.QUserInfoService
import javax.inject.Inject

interface IdentityManagerCallback {
    fun onSuccess(qonversionUserId: String)
    fun onError(error: QonversionError)
}

internal class QIdentityManager @Inject constructor(
    private val repository: QonversionRepository,
    private val userInfoService: QUserInfoService
) {
    val currentPartnersIdentityId: String? get() = userInfoService.getPartnersIdentityId()

    fun identify(userID: String, callback: IdentityManagerCallback) {
        val currentUserID = userInfoService.obtainUserID()
        repository.identify(userID, currentUserID,
            onSuccess = { resultUserID ->
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
