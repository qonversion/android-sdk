package com.qonversion.android.sdk.internal.billing.dataFetcher

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.exception.QonversionException

interface GoogleBillingDataFetcher {

    @Throws(QonversionException::class)
    suspend fun loadProducts(ids: List<String>): List<SkuDetails>

    @Throws(QonversionException::class)
    suspend fun queryPurchases(): List<Purchase>

    @Throws(QonversionException::class)
    suspend fun queryAllPurchasesHistory(): List<PurchaseHistory>

    suspend fun queryPurchasesHistory(
        @BillingClient.SkuType skuType: String
    ): Pair<BillingResult, List<PurchaseHistoryRecord>?>
}
