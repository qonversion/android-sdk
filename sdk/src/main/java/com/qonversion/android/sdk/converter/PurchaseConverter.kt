package com.qonversion.android.sdk.converter

import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.entity.Purchase

interface PurchaseConverter<F> {
    fun convertPurchase(purchaseInfo: F): Purchase?
    fun convertPurchases(
        skuDetails: Map<String, SkuDetails>,
        purchases: List<com.android.billingclient.api.Purchase>
    ): List<Purchase>
}
