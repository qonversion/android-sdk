package com.qonversion.android.sdk.internal.converter

import com.android.billingclient.api.*
import com.qonversion.android.sdk.internal.purchase.Purchase

internal interface PurchaseConverter<F> {
    fun convertPurchase(purchaseInfo: F): Purchase?
    fun convertPurchases(
        @Suppress("DEPRECATION") skuDetails: Map<String, SkuDetails>,
        purchases: List<com.android.billingclient.api.Purchase>
    ): List<Purchase>
}
