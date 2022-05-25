package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QEntitlement
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap

internal class EntitlementsManagerImpl @Inject constructor(
    private val permissionCallbacks: ConcurrentHashMap<String, MutableList<QonversionEntitlementsCallbackInternal>> = ConcurrentHashMap(),
    private val repository: QonversionRepository
): EntitlementsManager {

    override fun checkEntitlements(
        qonversionUserId: String,
        pendingIdentityUserId: String?,
        callback: QonversionEntitlementsCallbackInternal
    ) {
        val user = pendingIdentityUserId ?: qonversionUserId
        storeCallback(user, callback)
        if (pendingIdentityUserId != null || !permissionCallbacks[user].isNullOrEmpty()) {
            return
        }

        repository.entitlements(qonversionUserId, getResponseHandler(user))
    }

    override fun checkEntitlementsAfterIdentity(
        qonversionUserId: String,
        identityUserId: String,
        callback: QonversionEntitlementsCallbackInternal
    ) {
        val callbacks = permissionCallbacks[identityUserId]?.toList() ?: return

        permissionCallbacks.remove(identityUserId)
        storeCallbacks(qonversionUserId, callbacks)

        repository.entitlements(qonversionUserId, getResponseHandler(qonversionUserId))
    }

    private fun storeCallback(user: String, callback: QonversionEntitlementsCallbackInternal) {
        storeCallbacks(user, listOf(callback))
    }

    private fun storeCallbacks(user: String, callbacks: List<QonversionEntitlementsCallbackInternal>) {
        if (!permissionCallbacks.containsKey(user)) {
            permissionCallbacks[user] = mutableListOf()
        }
        permissionCallbacks[user]?.addAll(callbacks)
    }

    private fun getResponseHandler(user: String): QonversionEntitlementsCallbackInternal {
        return object : QonversionEntitlementsCallbackInternal {
            override fun onSuccess(entitlements: List<QEntitlement>) {
                val callbacks = permissionCallbacks[user]?.toList()
                permissionCallbacks.remove(user)
                callbacks?.forEach { it.onSuccess(entitlements) }
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                val callbacks = permissionCallbacks[user]?.toList()
                permissionCallbacks.remove(user)
                callbacks?.forEach { it.onError(error, responseCode) }
            }
        }
    }
}