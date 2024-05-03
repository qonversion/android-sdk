package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.services.QUserInfoService
import javax.inject.Inject

interface IdentityManagerCallback {
    fun onSuccess(qonversionUid: String)
    fun onError(error: QonversionError)
}

internal class QIdentityManager @Inject constructor(
    private val repository: QRepository,
    private val userInfoService: QUserInfoService
) {
    val currentPartnersIdentityId: String? get() = userInfoService.getPartnersIdentityId()

    fun identify(userID: String, callback: IdentityManagerCallback) {
        val currentUserID = userInfoService.obtainUserID()
        repository.identify(userID, currentUserID,
            onSuccess = { resultUserID ->
                userInfoService.storePartnersIdentityId(userID)
                if (resultUserID.isNotEmpty()) {
                    userInfoService.storeQonversionUserId(resultUserID)
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
