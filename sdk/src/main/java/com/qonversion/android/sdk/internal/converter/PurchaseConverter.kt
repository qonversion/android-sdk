package com.qonversion.android.sdk.internal.converter

import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.internal.dto.purchase.Purchase

internal interface PurchaseConverter {
    fun convertPurchase(purchase: com.android.billingclient.api.Purchase, options: QPurchaseOptions?): Purchase

    fun convertPurchases(
        purchases: List<com.android.billingclient.api.Purchase>,
        options: Map<String, QPurchaseOptions>?
    ): List<Purchase>
}
