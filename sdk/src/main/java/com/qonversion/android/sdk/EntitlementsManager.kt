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
    private val config: QonversionConfig,
    private val entitlementsCallbacks: ConcurrentHashMap<String, MutableList<QonversionEntitlementsCallbackInternal>> = ConcurrentHashMap()
) : QUserChangedListener {

    private var firstRequestExecuted = false

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

        if (pendingIdentityUserId.isNullOrEmpty() && !isRequestInProgress) {
            repository.entitlements(qonversionUserId, getResponseHandler(qonversionUserId, ignoreCache))
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
            repository.entitlements(qonversionUserId, getResponseHandler(qonversionUserId, ignoreCache = false))
        }
    }

    fun onIdentityFailedWithError(identityUserId: String, error: QonversionError) {
        fireErrorToListeners(identityUserId, error)
    }

    fun onRestore(qonversionUserId: String, entitlements: List<QEntitlement>) {
        cacheEntitlementsForUser(qonversionUserId, entitlements)
    }

    fun grantEntitlementsAfterFailedPurchaseTracking(
        qonversionUserId: String,
        purchase: Purchase,
        purchasedProduct: QProduct,
        productPermissions: Map<String, List<String>>
    ): List<QEntitlement> {
        val newEntitlements = productPermissions[purchasedProduct.qonversionID]?.map {
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
            .map { record ->
                val product = products.find { it.skuDetail?.sku === record.skuDetails?.sku }
                    ?: return@map emptyList<QEntitlement>()
                productPermissions[product.qonversionID]?.map {
                    createEntitlement(it, record.historyRecord.purchaseTime, product)
                } ?: emptyList()
            }
            .flatten()

        return mergeManuallyCreatedEntitlements(qonversionUserId, newEntitlements)
    }

    private fun mergeManuallyCreatedEntitlements(
        qonversionUserId: String,
        newEntitlements: List<QEntitlement>
    ): List<QEntitlement> {
        val existingEntitlements = cache.getActualStoredValue(true) ?: emptyList()
        val resultEntitlements = (newEntitlements + existingEntitlements)
            .groupBy { it.permissionID }
            .values
            .map { entitlementsWithSameId ->
                return@map entitlementsWithSameId
                    .filter { it.isActive }
                    .maxBy { it.expirationDate?.time ?: 0 }
                    ?: entitlementsWithSameId.first()
            }

        cacheEntitlementsForUser(qonversionUserId, resultEntitlements)

        return resultEntitlements
    }

    override fun onUserChanged(oldUid: String, newUid: String) {
        if (oldUid.isNotEmpty()) {
            cache.reset()
        }
    }

    @VisibleForTesting
    internal fun createEntitlement(id: String, purchaseTime: Long, purchasedProduct: QProduct): QEntitlement {
        val purchaseDuration = GoogleBillingPeriodConverter.convertSubscriptionPeriod(
            purchasedProduct.skuDetail?.subscriptionPeriod
        )

        val expirationDate = purchaseDuration?.let { Date(purchaseTime + it.toMs()) }
        val isActive = expirationDate == null || Date() < expirationDate

        return QEntitlement(
            id,
            Date(purchaseTime),
            expirationDate,
            isActive,
            QEntitlement.Product(
                purchasedProduct.qonversionID,
                QEntitlement.Product.Subscription(
                    QEntitlementRenewState.Unknown
                )
            )
        )
    }

    private fun isRequestInProgressForId(userId: String): Boolean {
        val callbackCount = entitlementsCallbacks[userId]?.size ?: 0
        return callbackCount > 0
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

    private fun getResponseHandler(
        qonversionUserId: String,
        ignoreCache: Boolean
    ): QonversionEntitlementsCallbackInternal {
        return object : QonversionEntitlementsCallbackInternal {
            override fun onSuccess(entitlements: List<QEntitlement>) {
                firstRequestExecuted = true

                cacheEntitlementsForUser(qonversionUserId, entitlements)

                fireToListeners(qonversionUserId) { onSuccess(entitlements) }
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                cache.takeUnless { ignoreCache }?.getActualStoredValue(isError = true)?.let {
                    fireToListeners(qonversionUserId) { onLoadedFromCache(it, error) }
                } ?: run {
                    fireErrorToListeners(qonversionUserId, error, responseCode)
                }
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
        val callbacks = entitlementsCallbacks[userId]?.toList()
        entitlementsCallbacks.remove(userId)
        callbacks?.forEach(callback)
    }
}
