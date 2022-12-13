package com.qonversion.android.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.BillingService
import com.qonversion.android.sdk.internal.billing.sku
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory

internal class Consumer internal constructor(
    private val billingService: BillingService,
    private val isAnalyticsMode: Boolean
) {
    fun consumePurchases(
        purchases: List<Purchase>,
        skuDetails: Map<String, SkuDetails>
    ) {
        if (isAnalyticsMode) {
            return
        }

        purchases.forEach { purchase ->
            val skuDetail = skuDetails[purchase.sku]
            skuDetail?.let { sku ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    consume(sku.type, purchase.purchaseToken, purchase.isAcknowledged)
                }
            }
        }
    }

    fun consumeHistoryRecords(records: List<PurchaseHistory>) {
        if (isAnalyticsMode) {
            return
        }

        records.forEach { record ->
            consume(record.type, record.historyRecord.purchaseToken, false)
        }
    }

    private fun consume(type: String, purchaseToken: String, isAcknowledged: Boolean) {
        if (type == BillingClient.SkuType.INAPP) {
            billingService.consume(purchaseToken)
        } else if (type == BillingClient.SkuType.SUBS && !isAcknowledged) {
            billingService.acknowledge(purchaseToken)
        }
    }
}
