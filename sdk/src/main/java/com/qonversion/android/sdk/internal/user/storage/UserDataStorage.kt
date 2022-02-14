package com.qonversion.android.sdk.internal.user.storage

internal interface UserDataStorage : UserDataProvider {

    fun setOriginalUserId(originalUserId: String)

    fun setIdentityUserId(identityUserId: String)

    fun clearIdentityUserId()
}
