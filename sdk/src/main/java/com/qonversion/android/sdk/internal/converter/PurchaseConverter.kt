package com.qonversion.android.sdk.internal.converter

import com.qonversion.android.sdk.internal.purchase.Purchase

internal interface PurchaseConverter {
    fun convertPurchase(purchase: com.android.billingclient.api.Purchase): Purchase

    fun convertPurchases(purchases: List<com.android.billingclient.api.Purchase>): List<Purchase>
}
