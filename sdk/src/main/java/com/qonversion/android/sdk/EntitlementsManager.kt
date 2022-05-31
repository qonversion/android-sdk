package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.storage.EntitlementsCache
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap

internal class EntitlementsManager @Inject constructor(
    private val repository: QonversionRepository,
    private val cache: EntitlementsCache,
    private val config: QonversionConfig,
    private val entitlementsCallbacks: ConcurrentHashMap<String, MutableList<QonversionEntitlementsCallbackInternal>> = ConcurrentHashMap()
) : QUserChangedListener {

    private var firstRequestExecuted = false

    init {
        config.subscribeOnUserChanges(this)
    }

    fun checkEntitlements(
        qonversionUserId: String,
        pendingIdentityUserId: String?,
        callback: QonversionEntitlementsCallbackInternal,
        ignoreCache: Boolean = false
    ) {
        if (firstRequestExecuted && !ignoreCache) {
            cache.getActualStoredValue()?.let {
                callback.onSuccess(it)
                return
            }
        }

        val userId = pendingIdentityUserId ?: qonversionUserId
        val isRequestInProgress = isRequestInProgressForId(userId)
        storeCallback(userId, callback)

        if (pendingIdentityUserId == null && !isRequestInProgress) {
            repository.entitlements(qonversionUserId, getResponseHandler(qonversionUserId))
        }
    }

    fun checkEntitlementsAfterIdentity(
        qonversionUserId: String,
        identityUserId: String
    ) {
        val callbacks = entitlementsCallbacks[identityUserId]?.toList() ?: return

        entitlementsCallbacks.remove(identityUserId)
        val isRequestInProgress = isRequestInProgressForId(qonversionUserId)
        storeCallbacks(qonversionUserId, callbacks)

        if (!isRequestInProgress) {
            repository.entitlements(qonversionUserId, getResponseHandler(qonversionUserId))
        }
    }

    fun onIdentityFailedWithError(identityUserId: String, error: QonversionError) {
        fireErrorToListeners(identityUserId, error)
    }

    override fun onUserChanged(oldUid: String, newUid: String) {
        if (oldUid.isNotEmpty()) {
            cache.reset()
        }
    }

    private fun isRequestInProgressForId(userId: String): Boolean {
        val callbackCount = entitlementsCallbacks[userId]?.size ?: 0
        return callbackCount > 0
    }

    private fun storeCallback(user: String, callback: QonversionEntitlementsCallbackInternal) {
        storeCallbacks(user, listOf(callback))
    }

    private fun storeCallbacks(user: String, callbacks: List<QonversionEntitlementsCallbackInternal>) {
        if (!entitlementsCallbacks.containsKey(user)) {
            entitlementsCallbacks[user] = mutableListOf()
        }
        entitlementsCallbacks[user]?.addAll(callbacks)
    }

    private fun getResponseHandler(qonversionUserId: String): QonversionEntitlementsCallbackInternal {
        return object : QonversionEntitlementsCallbackInternal {
            override fun onSuccess(entitlements: List<QEntitlement>) {
                firstRequestExecuted = true

                // Store only if the user had not changed
                if (qonversionUserId == config.uid) {
                    cache.store(entitlements)
                }

                fireEntitlementsToListeners(qonversionUserId, entitlements)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                cache.getActualStoredValue(true)?.let {
                    fireEntitlementsToListeners(qonversionUserId, it)
                } ?: run {
                    fireErrorToListeners(qonversionUserId, error, responseCode)
                }
            }
        }
    }

    private fun fireEntitlementsToListeners(
        qonversionUserId: String,
        entitlements: List<QEntitlement>
    ) {
        val callbacks = entitlementsCallbacks[qonversionUserId]?.toList()
        entitlementsCallbacks.remove(qonversionUserId)
        callbacks?.forEach { it.onSuccess(entitlements) }
    }

    private fun fireErrorToListeners(
        userId: String,
        error: QonversionError,
        responseCode: Int? = null
    ) {
        val callbacks = entitlementsCallbacks[userId]?.toList()
        entitlementsCallbacks.remove(userId)
        callbacks?.forEach { it.onError(error, responseCode) }
    }
}
