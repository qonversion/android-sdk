package com.qonversion.android.sdk

import com.android.billingclient.api.Purchase
import javax.inject.Inject

class QHandledPurchasesCache @Inject internal constructor() {
    private val handledPurchases = mutableSetOf<String>()

    fun shouldHandlePurchase(purchase: Purchase): Boolean {
        return !handledPurchases.contains(purchase.orderId)
    }

    fun saveHandledPurchase(purchase: Purchase) {
        handledPurchases.add(purchase.orderId)
    }

    fun saveHandledPurchases(purchases: Collection<Purchase>) {
        handledPurchases.addAll(purchases.map { it.orderId })
    }
}
