package com.qonversion.android.sdk.internal.converter

import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.internal.billing.productId
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.purchase.Purchase

internal class GooglePurchaseConverter : PurchaseConverter {

    override fun convertPurchases(
        purchases: List<com.android.billingclient.api.Purchase>,
        options: Map<String, QPurchaseOptions>?
    ): List<Purchase> {
        return purchases.map { convertPurchase(it, options?.get(it.productId)) }
    }

    override fun convertPurchase(purchase: com.android.billingclient.api.Purchase, options: QPurchaseOptions?): Purchase {
        return Purchase(
            storeProductId = purchase.productId,
            orderId = purchase.orderId ?: "",
            originalOrderId = formatOriginalTransactionId(purchase.orderId ?: ""),
            purchaseTime = purchase.purchaseTime.milliSecondsToSeconds(),
            purchaseToken = purchase.purchaseToken,
            contextKeys = options?.contextKeys
        )
    }

    private fun formatOriginalTransactionId(transactionId: String): String {
        val regex = Regex("\\.{2}.*")

        return regex.replace(transactionId, "")
    }
}
