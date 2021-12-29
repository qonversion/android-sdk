package com.qonversion.android.sdk.internal.billing.dataFetcher

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetailsParams
import com.qonversion.android.sdk.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.billing.utils.isOk
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class GoogleBillingDataFetcherImpl(
    logger: Logger
) : GoogleBillingDataFetcher, BaseClass(logger) {

    private lateinit var billingClient: BillingClient

    override fun setup(billingClient: BillingClient) {
        this.billingClient = billingClient
    }

    override suspend fun loadProducts(ids: Set<String>): List<SkuDetails> {
        val subs = querySkuDetails(BillingClient.SkuType.SUBS, ids).toMutableList()

        val subsIds = subs.map { it.sku }.toSet()
        val skuInApp = ids - subsIds

        return if (skuInApp.isNotEmpty()) {
            val inApps = querySkuDetails(BillingClient.SkuType.INAPP, skuInApp)

            subs + inApps
        } else subs
    }

    override suspend fun queryPurchases(): List<Purchase> {
        logger.verbose("queryPurchases() -> Querying purchases from cache for subs and inapp")

        val (subsBillingResult, subsPurchases) = fetchPurchases(BillingClient.SkuType.SUBS)

        val (inAppsBillingResult, inAppsPurchases) = fetchPurchases(BillingClient.SkuType.INAPP)

        val dataFetchedSuccessfully = subsBillingResult.isOk && inAppsBillingResult.isOk

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

        val dataFetchedSuccessfully = subsBillingResult.isOk && inAppsBillingResult.isOk

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
        logger.verbose("queryPurchasesHistory() -> Querying purchase history for type $skuType")
        return suspendCoroutine { continuation ->
            billingClient.queryPurchaseHistoryAsync(skuType) { billingResult, purchases ->
                continuation.resume(Pair(billingResult, purchases))
            }
        }
    }

    @Throws(QonversionException::class)
    suspend fun querySkuDetails(
        @BillingClient.SkuType productType: String,
        skuList: Set<String?>
    ): List<SkuDetails> {
        logger.verbose("querySkuDetails() -> Querying skuDetails for type $productType, " +
                "identifiers: ${skuList.joinToString()}")

        val params = buildSkuDetailsParams(productType, skuList)

        return suspendCoroutine { continuation ->
            billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                if (billingResult.isOk && skuDetailsList != null) {
                    logSkuDetails(skuDetailsList, skuList)
                    continuation.resume(skuDetailsList)
                } else {
                    var errorMessage = billingResult.getDescription() + '.'
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
            logger.info("queryAllPurchasesHistory() -> purchase history " +
                    "for $skuType is retrieved ${record.getDescription()}")
            PurchaseHistory(skuType, record)
        }
    }

    fun logSkuDetails(
        skuDetailsList: List<SkuDetails>,
        skuList: Set<String?>
    ) {
        if (skuDetailsList.isNotEmpty()) {
            skuDetailsList.forEach { logger.info("querySkuDetails() -> $it") }
        } else {
            logger.info("querySkuDetails() -> SkuDetails list for $skuList is empty.")
        }
    }

    private suspend fun fetchPurchases(@BillingClient.SkuType skuType: String): Pair<BillingResult, List<Purchase>> {
        logger.verbose("fetchPurchases() -> Querying purchases for type $skuType")
        return suspendCoroutine { continuation ->
            billingClient.queryPurchasesAsync(skuType) { billingResult, purchases ->
                continuation.resume(Pair(billingResult, purchases))
            }
        }
    }

    private fun buildSkuDetailsParams(
        @BillingClient.SkuType productType: String,
        skuList: Set<String?>
    ): SkuDetailsParams {
        return SkuDetailsParams.newBuilder()
            .setType(productType)
            .setSkusList(skuList.toList())
            .build()
    }
}
