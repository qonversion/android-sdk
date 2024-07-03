package com.qonversion.android.sdk.internal.converter

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.internal.billing.productId
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseData

internal class GooglePurchaseConverter : PurchaseConverter {

    override fun convertPurchases(
        purchases: List<Purchase>
    ): List<PurchaseData> {
        return purchases.map { convertPurchase(it) }
    }

    override fun convertPurchase(purchase: Purchase): PurchaseData {
        return PurchaseData(
            storeProductId = purchase.productId,
            orderId = purchase.orderId ?: "",
            originalOrderId = formatOriginalTransactionId(purchase.orderId ?: ""),
            purchaseTime = purchase.purchaseTime.milliSecondsToSeconds(),
            purchaseToken = purchase.purchaseToken,
        )
    }

    private fun formatOriginalTransactionId(transactionId: String): String {
        val regex = Regex("\\.{2}.*")

        return regex.replace(transactionId, "")
    }
}
