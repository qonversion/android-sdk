package com.qonversion.android.sdk.internal

import com.android.billingclient.api.Purchase
import javax.inject.Inject

internal class QHandledPurchasesCache @Inject internal constructor() {
    private val handledOrderIds = mutableSetOf<String>()

    fun shouldHandlePurchase(purchase: Purchase): Boolean {
        return !handledOrderIds.contains(purchase.orderId)
    }

    fun saveHandledPurchase(purchase: Purchase) {
        purchase.orderId?.let {
            handledOrderIds.add(it)
        }
    }

    fun saveHandledPurchases(purchases: Collection<Purchase>) {
        handledOrderIds.addAll(purchases.mapNotNull { it.orderId })
    }
}
