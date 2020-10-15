package com.qonversion.android.sdk.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.*
import com.qonversion.android.sdk.logger.Logger
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentLinkedQueue

class QonversionBillingService(
    private val application: Application,
    private val mainHandler: Handler,
    private val billingDelegate: BillingServiceDelegate,
    private val logger: Logger
) : PurchasesUpdatedListener, BillingClientStateListener, BillingService {

    private var billingClient: BillingClient? = null

    private val serviceRequests = ConcurrentLinkedQueue<(connectionError: PurchaseError?) -> Unit>()
    private val skuDetailsMap = mutableMapOf<String, SkuDetails>()

    init {
        billingClient = BillingClient
            .newBuilder(application)
            .enablePendingPurchases()
            .setListener(this)
            .build()

        startConnection()
    }

    override fun restore() {
        restoreAllPurchases(
            { allPurchases ->
                billingDelegate.handleRestoreCompletedFinished(allPurchases)
            },
            { error ->
                billingDelegate.handleRestoreCompletedFailed(error)
            }
        )
    }

    override fun purchase(
        activity: Activity,
        productID: String, @BillingClient.SkuType productType: String
    ) {
        var skuParams = SkuDetailsParams.newBuilder()
            .setType(productType)
            .setSkusList(listOf(productID))
            .build()

        withConnectedClient {
            billingClient?.querySkuDetailsAsync(skuParams) { billingResult, skuDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    logger.log("querySkuDetailsAsync - response code - ${billingResult.toReadableDescription()}")

                    if (!skuDetailsList.isNullOrEmpty()) {
                        logger.log("querySkuDetailsAsync - detail size - ${skuDetailsList.size}")
                        for (skuDetails in skuDetailsList) {
                            skuDetailsMap[skuDetails.sku] = skuDetails
                        }

                        executeRequestOnUIThread {
                            val params = BillingFlowParams.newBuilder()
                                .setSkuDetails(skuDetailsMap[productID]!!)
                                .build()

                            this@QonversionBillingService.launchBillingFlow(activity, params)
                        }
                    }
                }
            }
        }
    }

    @UiThread
    private fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams
    ) {
        withConnectedClient {
            launchBillingFlow(activity, params)
                .takeIf { billingResult -> billingResult?.responseCode != BillingClient.BillingResponseCode.OK }
                ?.let { billingResult ->
                    logger.log("Failed to launch billing intent. ${billingResult.toReadableDescription()}")
                }
        }
    }

    private fun restoreAllPurchases(
        onReceivePurchaseHistory: (List<PurchaseHistoryRecord>) -> Unit,
        onReceivePurchaseHistoryError: (PurchaseError) -> Unit
    ) {
        queryPurchaseHistoryAsync(
            BillingClient.SkuType.SUBS,
            { subsPurchasesList ->
                queryPurchaseHistoryAsync(
                    BillingClient.SkuType.INAPP,
                    { inAppPurchasesList ->
                        onReceivePurchaseHistory(
                            subsPurchasesList + inAppPurchasesList
                        )
                    },
                    onReceivePurchaseHistoryError
                )
            },
            onReceivePurchaseHistoryError
        )
    }

    private fun queryPurchaseHistoryAsync(
        @BillingClient.SkuType skuType: String,
        onReceivePurchaseHistory: (List<PurchaseHistoryRecord>) -> Unit,
        onReceivePurchaseHistoryError: (PurchaseError) -> Unit
    ) {
        logger.log("queryPurchaseHistoryAsync - for type $skuType")

        executeRequestOnUIThread { connectionError ->
            if (connectionError == null) {
                withConnectedClient {
                    queryPurchaseHistoryAsync(skuType)
                    { billingResult, purchaseHistoryRecordList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            purchaseHistoryRecordList
                                .takeUnless { it.isNullOrEmpty() }
                                ?.forEach {
                                    logger.log("Purchase history for $skuType is retrieved ${it.toReadableDescription()}")
                                }
                                ?: logger.log("Purchase history for $skuType is empty.")
                            onReceivePurchaseHistory(purchaseHistoryRecordList ?: emptyList())
                        } else {
                            onReceivePurchaseHistoryError(
                                PurchaseError(
                                    billingResult.responseCode,
                                    "Error receiving purchase history. ${billingResult.toReadableDescription()}"
                                )
                            )
                        }
                    }
                }
            } else {
                onReceivePurchaseHistoryError(connectionError)
            }
        }
    }

    private fun startConnection() {
        mainHandler.post {
            synchronized(this@QonversionBillingService) {
                billingClient?.let {
                    logger.log("Starting connection for $it")
                    it.startConnection(this)
                }
            }
        }
    }

    private fun executeRequestOnUIThread(request: (PurchaseError?) -> Unit) {
        synchronized(this@QonversionBillingService) {
            serviceRequests.add(request)
            if (billingClient?.isReady == false) {
                startConnection()
            } else {
                executePendingRequests()
            }
        }
    }

    private fun executePendingRequests() {
        synchronized(this@QonversionBillingService) {
            while (billingClient?.isReady == true && !serviceRequests.isEmpty()) {
                serviceRequests.remove()
                    .let {
                        mainHandler.post {
                            it(null)
                        }
                    }
            }
        }
    }

    private fun withConnectedClient(receivingFunction: BillingClient.() -> Unit) {
        billingClient
            ?.takeIf { it.isReady }
            ?.let {
                it.receivingFunction()
            }
            ?: logger.log("Warning: billing client is not ready, purchase and restore methods won't work. Stacktrace: ${getStackTrace()}")
    }

    private fun getStackTrace(): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        Throwable().printStackTrace(printWriter)
        return stringWriter.toString()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            logger.log("onPurchasesUpdated - purchases updated. ${billingResult.toReadableDescription()} ")
            billingDelegate.handlePurchaseCompletedFinished(purchases)
        } else {
            logger.log("onPurchasesUpdated - purchases failed to update. ${billingResult.toReadableDescription()}" +
                    "${purchases
                        ?.takeUnless { it.isEmpty() }
                        ?.let { purchase ->
                            "Purchases:" + purchase.joinToString(
                                ", ",
                                transform = { it.toReadableDescription() }
                            )
                        }}"
            )
            billingDelegate.handlePurchaseCompletedFailed(
                purchases, PurchaseError(
                    billingResult.responseCode,
                    "Error updating purchases. ${billingResult.toReadableDescription()}"
                )
            )
        }
    }

    override fun onBillingServiceDisconnected() {
        logger.log("Billing Service disconnected for ${billingClient?.toString()}")
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                logger.log("Billing Setup finished for ${billingClient?.toString()}.")
                executePendingRequests()
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                logger.log("Billing is not available in this device. ${billingResult.toReadableDescription()}")
                synchronized(this@QonversionBillingService) {
                    while (!serviceRequests.isEmpty()) {
                        serviceRequests.remove()
                            .let { serviceRequest ->
                                mainHandler.post {
                                    serviceRequest(
                                        PurchaseError(
                                            billingResult.responseCode,
                                            "Billing is not available in this device. ${billingResult.toReadableDescription()}"
                                        )
                                    )
                                }
                            }
                    }
                }
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                logger.log("Billing Setup finished with error code: ${billingResult.toReadableDescription()}")
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                // Billing service is already trying to connect. Don't do anything.
            }
        }
    }
}