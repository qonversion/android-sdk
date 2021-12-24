package com.qonversion.android.sdk.internal.billing.controller

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dto.BillingError
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.billing.utils.isOk
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.utils.currentFunctionName
import com.qonversion.android.sdk.internal.utils.sku
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal class GoogleBillingControllerImpl(
    private val consumer: GoogleBillingConsumer,
    private val purchaser: GoogleBillingPurchaser,
    private val dataFetcher: GoogleBillingDataFetcher,
    private val purchasesListener: PurchasesListener,
    logger: Logger
) : BaseClass(logger), GoogleBillingController {

    @set:Synchronized
    @get:Synchronized
    @Volatile
    var billingClient: BillingClient? = null
        set(value) {
            field = value
            @Suppress("DeferredResultUnused")
            value?.let {
                consumer.setup(value)
                purchaser.setup(value)
                dataFetcher.setup(value)
                connectToBillingAsync()
            }
        }

    @Volatile
    var connectionDeferred: CompletableDeferred<BillingError?>? = null

    val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.isOk && purchases != null) {
            logger.debug("onPurchasesUpdated() -> purchases updated. ${billingResult.getDescription()} ")
            purchasesListener.onPurchasesCompleted(purchases)
        } else {
            val errorMessage = if (
                billingResult.isOk && purchases == null
            ) {
                "No purchase was passed for successful billing result."
            } else {
                billingResult.getDescription()
            }
            purchasesListener.onPurchasesFailed(
                purchases ?: emptyList(),
                BillingError(billingResult.responseCode, errorMessage)
            )

            logger.release("onPurchasesUpdated() -> failed to update purchases $errorMessage")
            if (!purchases.isNullOrEmpty()) {
                logger.release(
                    "Purchases: " + purchases.joinToString(
                        ", ",
                        transform = { it.getDescription() })
                )
            }
        }
    }

    var billingClientStateListener = object : BillingClientStateListener {
        override fun onBillingServiceDisconnected() {
            logger.debug("billingClientStateListener -> BillingClient disconnected ($billingClient).")
        }

        override fun onBillingSetupFinished(billingResult: BillingResult) {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    logger.debug(
                        "billingClientStateListener -> BillingClient successfully connected ($billingClient)."
                    )
                    completeConnectionDeferred()
                }
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                    logger.release(
                        "billingClientStateListener -> BillingClient connection failed with error: " +
                                "${billingResult.getDescription()} ($billingClient)."
                    )
                    val error = BillingError(
                        billingResult.responseCode,
                        "Billing is not available on this device. ${billingResult.getDescription()}"
                    )
                    completeConnectionDeferred(error)
                }
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                    // Client is already in the process of connecting to billing service
                }
                else -> {
                    // These errors might be fixed for the next attempt, so we don't do anything here
                    logger.release(
                        "billingClientStateListener -> BillingClient connection failed with error: " +
                                "${billingResult.getDescription()} ($billingClient)."
                    )
                }
            }
        }
    }

    override suspend fun queryPurchasesHistory(): List<PurchaseHistory> {
        waitForReadyClient()

        return dataFetcher.queryAllPurchasesHistory()
    }

    override suspend fun queryPurchases(): List<Purchase> {
        waitForReadyClient()

        return dataFetcher.queryPurchases()
    }

    override suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails?,
        prorationMode: Int?
    ) {
        waitForReadyClient()

        if (oldSkuDetails == null) {
            purchaser.purchase(activity, skuDetails)
        } else {
            val (billingResult, purchasesHistory) = dataFetcher.queryPurchasesHistory(skuDetails.type)
            if (!billingResult.isOk || purchasesHistory == null) {
                val error = BillingError(
                    billingResult.responseCode,
                    "Failed to fetch history records for sku type ${skuDetails.type}. ${billingResult.getDescription()}"
                )
                throw error.toQonversionException()
            }

            val purchaseHistoryRecord = purchasesHistory.find { it.sku == oldSkuDetails.sku }
                ?: throw QonversionException(ErrorCode.Purchasing, "No existing purchase for sku: ${oldSkuDetails.sku}")
            val updatePurchaseInfo = UpdatePurchaseInfo(purchaseHistoryRecord.purchaseToken, prorationMode)
            purchaser.purchase(activity, skuDetails, updatePurchaseInfo)
        }
    }

    override suspend fun loadProducts(productIds: Set<String>): List<SkuDetails> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        waitForReadyClient()

        return dataFetcher.loadProducts(productIds)
    }

    override suspend fun getSkuDetailsFromPurchases(purchases: List<Purchase>): List<SkuDetails> {
        val skuList = purchases.mapNotNull { it.sku }.toSet()
        return loadProducts(skuList)
    }

    override suspend fun consume(purchaseToken: String) {
        try {
            waitForReadyClient()
        } catch (e: QonversionException) {
            logger.release(e.toString())
            return
        }

        try {
            consumer.consume(purchaseToken)
        } catch (e: QonversionException) {
            logger.release("Failed to consume purchase with token $purchaseToken: $e")
        }
    }

    override suspend fun acknowledge(purchaseToken: String) {
        try {
            waitForReadyClient()
        } catch (e: QonversionException) {
            logger.release(e.toString())
            return
        }

        try {
            consumer.acknowledge(purchaseToken)
        } catch (e: QonversionException) {
            logger.release("Failed to acknowledge purchase with token $purchaseToken: $e")
        }
    }

    @Synchronized
    fun completeConnectionDeferred(error: BillingError? = null) {
        connectionDeferred?.complete(error)
        connectionDeferred = null
    }

    @Synchronized
    fun connectToBillingAsync(): Deferred<BillingError?> {
        return connectionDeferred ?: CompletableDeferred<BillingError?>().also {
            billingClient?.let { client ->
                connectionDeferred = it
                client.startConnection(billingClientStateListener)
                logger.debug("Trying to connect to BillingClient ($billingClient)")
            } ?: throw QonversionException(ErrorCode.BillingConnection, "Billing client is not set.")
        }
    }

    suspend fun waitForReadyClient() {
        if (billingClient?.isReady == true) {
            return
        }

        val billingError = connectToBillingAsync().await()
        if (billingError != null) {
            throw billingError.toQonversionException()
        }
    }
}
