package com.qonversion.android.sdk

internal interface EntitlementsManager {

    fun checkEntitlements(
        qonversionUserId: String,
        pendingIdentityUserId: String?,
        callback: QonversionEntitlementsCallbackInternal
    )

    fun checkEntitlementsAfterIdentity(
        qonversionUserId: String,
        identityUserId: String,
        callback: QonversionEntitlementsCallbackInternal
    )
}