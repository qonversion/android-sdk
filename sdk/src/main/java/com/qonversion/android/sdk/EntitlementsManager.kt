package com.qonversion.android.sdk

import androidx.annotation.VisibleForTesting
import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.converter.GoogleBillingPeriodConverter
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QEntitlementCacheLifetime
import com.qonversion.android.sdk.dto.QEntitlementRenewState
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.entity.PurchaseHistory
import com.qonversion.android.sdk.storage.EntitlementsCache
import javax.inject.Inject
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

internal class EntitlementsManager @Inject constructor(
    private val repository: QonversionRepository,
    private val cache: EntitlementsCache,
    private val config: QonversionConfig
) : QUserChangedListener {

    private var firstRequestExecuted = false
    private val entitlementsCallbacks: ConcurrentHashMap<String, MutableList<QonversionEntitlementsCallbackInternal>> =
        ConcurrentHashMap()
    private val requestsInProgress: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    init {
        config.subscribeOnUserChanges(this)
    }

    fun setCacheLifetime(lifetime: QEntitlementCacheLifetime) {
        cache.setCacheLifetime(lifetime)
    }

    fun checkEntitlements(
        qonversionUserId: String,
        pendingIdentityUserId: String?,
        callback: QonversionEntitlementsCallbackInternal,
        isLaunchInProgress: Boolean,
        ignoreCache: Boolean = false
    ) {
        if (firstRequestExecuted && !ignoreCache) {
            cache.getActualStoredValue()?.let {
                callback.onSuccess(it)
                return
            }
        }

        val userId = pendingIdentityUserId ?: qonversionUserId
        storeCallback(userId, callback.wrapWithCacheHandling(ignoreCache))

        if (!isLaunchInProgress && pendingIdentityUserId.isNullOrEmpty()) {
            requestEntitlements(qonversionUserId)
        }
    }

    fun onLaunchSucceeded(qonversionUserId: String) {
        requestEntitlements(qonversionUserId)
    }

    fun onIdentitySucceeded(
        qonversionUserId: String,
        identityUserId: String,
        isLaunchInProgress: Boolean
    ) {
        val callbacks = entitlementsCallbacks[identityUserId]?.toList() ?: return

        entitlementsCallbacks.remove(identityUserId)
        storeCallbacks(qonversionUserId, callbacks)

        if (!isLaunchInProgress) {
            requestEntitlements(qonversionUserId)
        }
    }

    fun onIdentityFailedWithError(identityUserId: String, error: QonversionError) {
        fireErrorToListeners(identityUserId, error)
    }

    fun grantEntitlementsAfterFailedPurchaseTracking(
        qonversionUserId: String,
        purchase: Purchase,
        purchasedProduct: QProduct,
        productPermissions: Map<String, List<String>>
    ): List<QEntitlement> {
        val newEntitlements = productPermissions[purchasedProduct.qonversionID]?.mapNotNull {
            createEntitlement(it, purchase.purchaseTime, purchasedProduct)
        } ?: emptyList()

        return mergeManuallyCreatedEntitlements(qonversionUserId, newEntitlements)
    }

    fun grantEntitlementsAfterFailedRestore(
        qonversionUserId: String,
        historyRecords: List<PurchaseHistory>,
        products: Collection<QProduct>,
        productPermissions: Map<String, List<String>>
    ): List<QEntitlement> {
        val newEntitlements = historyRecords
            .filter { it.skuDetails != null }
            .mapNotNull { record ->
                val product = products.find { it.skuDetail?.sku === record.skuDetails?.sku }
                product?.let {
                    productPermissions[product.qonversionID]?.map {
                        createEntitlement(it, record.historyRecord.purchaseTime, product)
                    }
                }
            }
            .flatten()
            .filterNotNull()

        return mergeManuallyCreatedEntitlements(qonversionUserId, newEntitlements)
    }

    fun resetCache() {
        cache.reset()
    }

    override fun onUserChanged(oldUid: String, newUid: String) {
        if (oldUid.isNotEmpty()) {
            resetCache()
        }
    }

    private fun mergeManuallyCreatedEntitlements(
        qonversionUserId: String,
        newEntitlements: List<QEntitlement>
    ): List<QEntitlement> {
        val existingEntitlements = cache.getActualStoredValue(true) ?: emptyList()
        val resultEntitlementsMap =
            existingEntitlements.associateBy { it.permissionID }.toMutableMap()

        newEntitlements.forEach { newEntitlement ->
            val id = newEntitlement.permissionID
            resultEntitlementsMap[id] =
                chooseEntitlementToSave(resultEntitlementsMap[id], newEntitlement)
        }

        val resultEntitlements = resultEntitlementsMap.values.toList()
        cacheEntitlementsForUser(qonversionUserId, resultEntitlements)

        return resultEntitlements
    }

    private fun chooseEntitlementToSave(
        existingEntitlement: QEntitlement?,
        localCreatedEntitlement: QEntitlement
    ): QEntitlement {
        existingEntitlement ?: return localCreatedEntitlement

        // If expiration date is null then it's permanent entitlements and thus
        // should take precedence over expiring one.
        val newPermissionExpirationTime =
            localCreatedEntitlement.expirationDate?.time ?: Long.MAX_VALUE
        val existingPermissionExpirationTime =
            existingEntitlement.expirationDate?.time ?: Long.MAX_VALUE
        val doesNewOneExpireLater =
            newPermissionExpirationTime > existingPermissionExpirationTime

        // replace if new entitlement is active and expires later
        return if (!existingEntitlement.isActive || doesNewOneExpireLater) {
            localCreatedEntitlement
        } else {
            existingEntitlement
        }
    }

    @VisibleForTesting
    internal fun createEntitlement(
        id: String,
        purchaseTime: Long,
        purchasedProduct: QProduct
    ): QEntitlement? {
        val purchaseDurationDays = GoogleBillingPeriodConverter.convertPeriodToDays(
            purchasedProduct.skuDetail?.subscriptionPeriod
        )

        val expirationDate = purchaseDurationDays?.let { Date(purchaseTime + it.daysToMs) }

        return if (expirationDate == null || Date() < expirationDate) {
            QEntitlement(
                id,
                Date(purchaseTime),
                expirationDate,
                true,
                QEntitlement.Product(
                    purchasedProduct.qonversionID,
                    QEntitlement.Product.Subscription(
                        QEntitlementRenewState.Unknown
                    )
                )
            )
        } else null
    }

    private fun requestEntitlements(qonversionUserId: String) {
        if (isRequestInProgressForId(qonversionUserId)) {
            return
        }

        requestsInProgress[qonversionUserId] = true
        repository.entitlements(qonversionUserId, getResponseHandler(qonversionUserId))
    }

    private fun isRequestInProgressForId(userId: String): Boolean {
        return requestsInProgress[userId] == true
    }

    private fun storeCallback(userId: String, callback: QonversionEntitlementsCallbackInternal) {
        storeCallbacks(userId, listOf(callback))
    }

    private fun storeCallbacks(
        userId: String,
        callbacks: List<QonversionEntitlementsCallbackInternal>
    ) {
        val list = entitlementsCallbacks[userId] ?: mutableListOf()
        list.addAll(callbacks)
        entitlementsCallbacks[userId] = list
    }

    private fun getResponseHandler(qonversionUserId: String): QonversionEntitlementsCallbackInternal {
        return object : QonversionEntitlementsCallbackInternal {
            override fun onSuccess(entitlements: List<QEntitlement>) {
                requestsInProgress[qonversionUserId] = false
                firstRequestExecuted = true

                cacheEntitlementsForUser(qonversionUserId, entitlements)

                fireToListeners(qonversionUserId) { onSuccess(entitlements) }
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                requestsInProgress[qonversionUserId] = false

                fireErrorToListeners(qonversionUserId, error, responseCode)
            }
        }
    }

    private fun QonversionEntitlementsCallbackInternal.wrapWithCacheHandling(
        ignoreCache: Boolean
    ): QonversionEntitlementsCallbackInternal =
        object : QonversionEntitlementsCallbackInternal {
            override fun onSuccess(entitlements: List<QEntitlement>) {
                this@wrapWithCacheHandling.onSuccess(entitlements)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                cache.takeUnless { ignoreCache }?.getActualStoredValue(isError = true)?.let {
                    this@wrapWithCacheHandling.onLoadedFromCache(it, error)
                } ?: run {
                    this@wrapWithCacheHandling.onError(error, responseCode)
                }
            }
        }

    @VisibleForTesting
    internal fun cacheEntitlementsForUser(
        qonversionUserId: String,
        entitlements: List<QEntitlement>
    ) {
        // Store only if the user has not changed
        if (qonversionUserId == config.uid) {
            cache.store(entitlements)
        }
    }

    private fun fireErrorToListeners(
        userId: String,
        error: QonversionError,
        responseCode: Int? = null
    ) {
        fireToListeners(userId) { onError(error, responseCode) }
    }

    private fun fireToListeners(
        userId: String,
        callback: QonversionEntitlementsCallbackInternal.() -> Unit
    ) {
        entitlementsCallbacks
            .remove(userId)
            ?.toList()
            ?.forEach(callback)
    }
}
