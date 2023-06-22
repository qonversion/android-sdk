package com.qonversion.android.sdk.internal

import com.android.billingclient.api.Purchase
import javax.inject.Inject

internal class QHandledPurchasesCache @Inject internal constructor() {
    private val handledOrderIDs = mutableSetOf<String>()

    fun shouldHandlePurchase(purchase: Purchase): Boolean {
        return !handledOrderIDs.contains(purchase.orderId)
    }

    fun saveHandledPurchase(purchase: Purchase) {
        purchase.orderId?.let {
            handledOrderIDs.add(it)
        }
    }

    fun saveHandledPurchases(purchases: Collection<Purchase>) {
        handledOrderIDs.addAll(purchases.mapNotNull { it.orderId })
    }
}
