package com.qonversion.android.sdk.internal.billing.dataFetcher

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.dto.PurchaseHistory

interface GoogleBillingDataFetcher {
    suspend fun loadProducts(ids: List<String>): List<SkuDetails>
    suspend fun queryPurchases(): List<Purchase>
    suspend fun queryAllPurchasesHistory(): List<PurchaseHistory>
    suspend fun fetchPurchasesHistory(
        @BillingClient.SkuType skuType: String
    ): Pair<BillingResult, List<PurchaseHistoryRecord>?>
}