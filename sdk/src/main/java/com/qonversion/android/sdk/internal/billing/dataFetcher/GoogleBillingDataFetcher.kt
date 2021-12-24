package com.qonversion.android.sdk.internal.billing.dataFetcher

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.billing.GoogleBillingHelper
import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface GoogleBillingDataFetcher : GoogleBillingHelper {

    @Throws(QonversionException::class)
    suspend fun loadProducts(ids: Set<String>): List<SkuDetails>

    @Throws(QonversionException::class)
    suspend fun queryPurchases(): List<Purchase>

    @Throws(QonversionException::class)
    suspend fun queryAllPurchasesHistory(): List<PurchaseHistory>

    suspend fun queryPurchasesHistory(
        @BillingClient.SkuType skuType: String
    ): Pair<BillingResult, List<PurchaseHistoryRecord>?>
}
