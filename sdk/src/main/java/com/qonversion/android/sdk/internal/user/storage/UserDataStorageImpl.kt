package com.qonversion.android.sdk.internal.user.storage

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

internal class UserDataStorageImpl(
    private val localStorage: LocalStorage,
) : UserDataStorage {

    @VisibleForTesting
    var originalId: String? = localStorage.getString(StorageConstants.OriginalUserId.key)

    @VisibleForTesting
    var identityId: String? = localStorage.getString(StorageConstants.UserId.key)

    override fun getUserId(): String? = identityId ?: originalId

    override fun requireUserId(): String = getUserId()
        ?: throw QonversionException(
            ErrorCode.UserNotFound,
            "The user id was required but does not exist."
        )

    override fun setOriginalUserId(originalUserId: String) {
        localStorage.putString(StorageConstants.OriginalUserId.key, originalUserId)
        originalId = originalUserId
    }

    override fun setIdentityUserId(identityUserId: String) {
        localStorage.putString(StorageConstants.UserId.key, identityUserId)
        identityId = identityUserId
    }

    override fun clearIdentityUserId() {
        localStorage.remove(StorageConstants.UserId.key)
        identityId = null
    }
}
