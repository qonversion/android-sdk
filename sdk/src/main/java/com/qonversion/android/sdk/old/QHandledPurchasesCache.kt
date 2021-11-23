package com.qonversion.android.sdk.old

import com.android.billingclient.api.Purchase
import javax.inject.Inject

class QHandledPurchasesCache @Inject internal constructor() {
    private val handledOrderIDs = mutableSetOf<String>()

    fun shouldHandlePurchase(purchase: Purchase): Boolean {
        return !handledOrderIDs.contains(purchase.orderId)
    }

    fun saveHandledPurchase(purchase: Purchase) {
        handledOrderIDs.add(purchase.orderId)
    }

    fun saveHandledPurchases(purchases: Collection<Purchase>) {
        handledOrderIDs.addAll(purchases.map { it.orderId })
    }
}
