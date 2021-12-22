package com.qonversion.android.sdk.internal.billing.dataFetcher

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetailsParams
import com.qonversion.android.sdk.internal.billing.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class GoogleBillingDataFetcherImpl(
    private val billingClient: BillingClient,
    logger: Logger
) : GoogleBillingDataFetcher, BaseClass(logger) {

    override suspend fun loadProducts(ids: List<String>): List<SkuDetails> {
        val subs = querySkuDetails(BillingClient.SkuType.SUBS, ids).toMutableList()

        val subsIds = subs.map { it.sku }.toSet()
        val skuInApp = ids - subsIds

        return if (skuInApp.isNotEmpty()) {
            val inApps = querySkuDetails(BillingClient.SkuType.INAPP, skuInApp)

            subs + inApps
        } else subs
    }

    override suspend fun queryPurchases(): List<Purchase> {
        logger.debug("queryPurchases() -> Querying purchases from cache for subs and inapp")

        val (subsBillingResult, subsPurchases) = fetchPurchases(BillingClient.SkuType.SUBS)

        val (inAppsBillingResult, inAppsPurchases) = fetchPurchases(BillingClient.SkuType.INAPP)

        val dataFetchedSuccessfully = subsBillingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                inAppsBillingResult.responseCode == BillingClient.BillingResponseCode.OK

        if (dataFetchedSuccessfully) {
            return subsPurchases + inAppsPurchases
        } else {
            val explanation = "Subs result: ${subsBillingResult.responseCode.getDescription()}. " +
                    "Inapp result: ${inAppsBillingResult.responseCode.getDescription()}"
            throw QonversionException(ErrorCode.PurchasesFetching, explanation)
        }
    }

    override suspend fun queryAllPurchasesHistory(): List<PurchaseHistory> {
        val (subsBillingResult, subsPurchaseHistory) = queryPurchasesHistory(BillingClient.SkuType.SUBS)

        val (inAppsBillingResult, inAppsPurchaseHistory) = queryPurchasesHistory(BillingClient.SkuType.INAPP)

        val dataFetchedSuccessfully = subsBillingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                inAppsBillingResult.responseCode == BillingClient.BillingResponseCode.OK

        if (dataFetchedSuccessfully) {
            val subsHistoryRecords = getHistoryFromRecords(BillingClient.SkuType.SUBS, subsPurchaseHistory)
            val inAppsHistoryRecords = getHistoryFromRecords(BillingClient.SkuType.INAPP, inAppsPurchaseHistory)
            return subsHistoryRecords + inAppsHistoryRecords
        } else {
            val explanation = "Subs result: ${subsBillingResult.responseCode.getDescription()}. " +
                    "Inapp result: ${inAppsBillingResult.responseCode.getDescription()}"
            throw QonversionException(ErrorCode.PurchasesHistoryFetching, explanation)
        }
    }

    override suspend fun queryPurchasesHistory(
        @BillingClient.SkuType skuType: String
    ): Pair<BillingResult, List<PurchaseHistoryRecord>?> {
        logger.debug("queryPurchasesHistory() -> Querying purchase history for type $skuType")
        return suspendCoroutine { continuation ->
            billingClient.queryPurchaseHistoryAsync(skuType) { billingResult, purchases ->
                continuation.resume(Pair(billingResult, purchases))
            }
        }
    }

    @Throws(QonversionException::class)
    suspend fun querySkuDetails(
        @BillingClient.SkuType productType: String,
        skuList: List<String?>
    ): List<SkuDetails> {
        logger.debug("querySkuDetails() -> Querying skuDetails for type $productType, " +
                "identifiers: ${skuList.joinToString()}")

        val params = buildSkuDetailsParams(productType, skuList)

        return suspendCoroutine { continuation ->
            billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                    logSkuDetails(skuDetailsList, skuList)
                    continuation.resume(skuDetailsList)
                } else {
                    var errorMessage = "Failed to fetch products."
                    if (skuDetailsList == null) {
                        errorMessage += " SkuDetails list for $skuList is null."
                    }

                    throw QonversionException(ErrorCode.SkuDetailsFetching, errorMessage)
                }
            }
        }
    }

    fun getHistoryFromRecords(
        @BillingClient.SkuType skuType: String,
        historyRecords: List<PurchaseHistoryRecord>?
    ): List<PurchaseHistory> {
        if (historyRecords == null) {
            return emptyList()
        }

        return historyRecords.map { record ->
            logger.debug("queryAllPurchasesHistory() -> purchase history " +
                    "for $skuType is retrieved ${record.getDescription()}")
            PurchaseHistory(skuType, record)
        }
    }

    fun logSkuDetails(
        skuDetailsList: List<SkuDetails>,
        skuList: List<String?>
    ) {
        if (skuDetailsList.isNotEmpty()) {
            skuDetailsList.forEach { logger.debug("querySkuDetails() -> $it") }
        } else {
            logger.release("querySkuDetails() -> SkuDetails list for $skuList is empty.")
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
