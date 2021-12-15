package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetailsParams
import com.qonversion.android.sdk.internal.billing.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class GoogleBillingDataFetcher(
    private val billingClient: BillingClient,
    logger: Logger
) : BaseClass(logger) {

    @Throws(QonversionException::class)
    suspend fun loadProducts(ids: List<String>): List<SkuDetails> {
        val skuDetails = querySkuDetails(BillingClient.SkuType.SUBS, ids).toMutableList()

        val subsIds = skuDetails.map { it.sku }.toSet()
        val skuInApp = ids - subsIds

        if (skuInApp.isNotEmpty()) {
            val inApps = querySkuDetails(BillingClient.SkuType.INAPP, skuInApp)

            skuDetails.addAll(inApps)
        }

        return skuDetails.toList()
    }

    @Throws(QonversionException::class)
    suspend fun querySkuDetails(
        @BillingClient.SkuType productType: String,
        skuList: List<String?>
    ): List<SkuDetails> {
        logger.debug("querySkuDetailsAsync() -> Querying skuDetails for type $productType, " +
                "identifiers: ${skuList.joinToString()}")

        val params = buildSkuDetailsParams(productType, skuList)

        return suspendCoroutine { continuation ->
            billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                    logSkuDetails(skuDetailsList, skuList)
                    continuation.resume(skuDetailsList)
                } else {
                    var errorMessage = "Failed to fetch products. "
                    if (skuDetailsList == null) {
                        errorMessage += "SkuDetails list for $skuList is null. "
                    }

                    throw QonversionException(ErrorCode.SkuDetailsFetching)
                }
            }
        }
    }

    @Throws(QonversionException::class)
    suspend fun queryPurchases(): List<Purchase> {
        logger.debug("queryPurchases() -> Querying purchases from cache for subs and inapp")

        val (subsBillingResult, subsPurchases) = fetchPurchases(BillingClient.SkuType.SUBS)

        val (inAppsBillingResult, inAppsPurchases) = fetchPurchases(BillingClient.SkuType.INAPP)

        val dataFetchedSuccessfully = subsBillingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                inAppsBillingResult.responseCode == BillingClient.BillingResponseCode.OK

        if (dataFetchedSuccessfully) {
            return subsPurchases + inAppsPurchases
        } else {
            throw QonversionException(ErrorCode.PurchasesFetching)
        }
    }

    @Throws(QonversionException::class)
    suspend fun queryAllPurchasesHistory(): List<PurchaseHistory> {
        // 4 testa na vse case, 1 ok, 2 ok, oba ok, oba ne ok
        val (subsBillingResult, subsPurchaseHistory) = fetchPurchasesHistory(BillingClient.SkuType.SUBS)

        val (inAppsBillingResult, inAppsPurchaseHistory) = fetchPurchasesHistory(BillingClient.SkuType.INAPP)

        val dataFetchedSuccessfully = subsBillingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                inAppsBillingResult.responseCode == BillingClient.BillingResponseCode.OK

        if (dataFetchedSuccessfully) {
            val subsHistoryRecords = getHistoryFromRecords(BillingClient.SkuType.SUBS, subsPurchaseHistory)
            val inAppsHistoryRecords = getHistoryFromRecords(BillingClient.SkuType.INAPP, inAppsPurchaseHistory)
            return subsHistoryRecords + inAppsHistoryRecords
        } else {
            throw QonversionException(ErrorCode.PurchasesHistoryFetching)
        }
    }

    fun getHistoryFromRecords(
        @BillingClient.SkuType skuType: String,
        historyRecords: List<PurchaseHistoryRecord>?
    ): List<PurchaseHistory> {
        if (historyRecords == null) {
            return emptyList()
        }

        val purchaseHistory = mutableListOf<PurchaseHistory>()
        historyRecords.forEach { record ->
            purchaseHistory.add(PurchaseHistory(skuType, record))
            logger.debug("queryPurchaseHistoryAsync() -> purchase history " +
                    "for $skuType is retrieved ${record.skus}")
        }

        return purchaseHistory
    }

    suspend fun fetchPurchasesHistory(
        @BillingClient.SkuType skuType: String
    ): Pair<BillingResult, List<PurchaseHistoryRecord>?> {
        logger.debug("fetchPurchasesHistory() -> Querying purchase history for type $skuType")
        return suspendCoroutine { continuation ->
            billingClient.queryPurchaseHistoryAsync(skuType) { billingResult, purchases ->
                continuation.resume(Pair(billingResult, purchases))
            }
        }
    }

    fun logSkuDetails(
        skuDetailsList: List<SkuDetails>,
        skuList: List<String?>
    ) {
        if (skuDetailsList.isNotEmpty()) {
            skuDetailsList.forEach { logger.debug("querySkuDetailsAsync() -> $it") }
        } else {
            logger.release("querySkuDetailsAsync() -> SkuDetails list for $skuList is empty.")
        }
    }

    private suspend fun fetchPurchases(@BillingClient.SkuType skuType: String): Pair<BillingResult, List<Purchase>> {
        logger.debug("fetchPurchases() -> Querying purchases for type $skuType")
        return suspendCoroutine { continuation ->
            billingClient.queryPurchasesAsync(skuType) { billingResult, purchases ->
                continuation.resume(Pair(billingResult, purchases))
            }
        }
    }

    private fun buildSkuDetailsParams(
        @BillingClient.SkuType productType: String,
        skuList: List<String?>
    ): SkuDetailsParams {
        return SkuDetailsParams.newBuilder()
            .setType(productType)
            .setSkusList(skuList)
            .build()
    }
}
