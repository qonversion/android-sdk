package com.qonversion.android.sdk.internal.converter

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseData

internal interface PurchaseConverter {
    fun convertPurchase(purchase: Purchase): PurchaseData

    fun convertPurchases(purchases: List<Purchase>): List<PurchaseData>
}
