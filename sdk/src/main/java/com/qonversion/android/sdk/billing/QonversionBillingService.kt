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
    private val purchasesListener: PurchasesListener,
    private val logger: Logger
) : PurchasesUpdatedListener, BillingClientStateListener, BillingService {

    private var billingClient: BillingClient? = null

    private val serviceRequests = ConcurrentLinkedQueue<(connectionError: BillingError?) -> Unit>()

    init {
        billingClient = BillingClient
            .newBuilder(application)
            .enablePendingPurchases()
            .setListener(this)
            .build()

        startConnection()
    }

    interface PurchasesListener {
        fun onPurchasesCompleted(purchases: List<Purchase>)
        fun onPurchasesFailed(
            purchases: List<Purchase>?,
            error: BillingError
        )
    }

    override fun restore(
        onRestoreCompleted: (purchases: List<PurchaseHistoryRecord>) -> Unit,
        onRestoreFailed: (error: BillingError) -> Unit
    ) {
        queryAllPurchases(
            { allPurchases ->
                onRestoreCompleted(allPurchases)
            },
            { error ->
                logger.log(error.toString())
                onRestoreFailed(error)
            }
        )
    }

    override fun loadProducts(
        products: Set<Product>,
        onLoadCompleted: (products: List<SkuDetails>) -> Unit,
        onLoadFailed: (error: BillingError) -> Unit
    ) {
        loadAllProducts(
            products,
            { allProducts ->
                onLoadCompleted(allProducts)
            },
            { error ->
                logger.log(error.toString())
                onLoadFailed(error)
            }
        )
    }

    override fun purchase(
        activity: Activity,
        skuDetails: SkuDetails
    ) {
        logger.log("Start purchasing for sku: ${skuDetails.sku}")

        executeRequestOnUIThread {
            val params = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build()

            this@QonversionBillingService.launchBillingFlow(activity, params)
        }
    }

    override fun consume(
        purchaseToken: String,
        onConsumed: (billingResult: BillingResult, purchaseToken: String) -> Unit
    ) {
        logger.log("Consume purchase with token $purchaseToken")
        executeRequestOnUIThread { connectionError ->
            if (connectionError == null) {
                withConnectedClient {
                    consumeAsync(
                        ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build(),
                        onConsumed
                    )
                }
            }
        }
    }

    override fun acknowledge(
        purchaseToken: String,
        onAcknowledged: (billingResult: BillingResult, purchaseToken: String) -> Unit
    ) {
        logger.log("Acknowledge purchase with token $purchaseToken")
        executeRequestOnUIThread { connectionError ->
            if (connectionError == null) {
                withConnectedClient {
                    acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken)
                            .build()
                    ) { billingResult ->
                        onAcknowledged(billingResult, purchaseToken)
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
                    logger.log("Failed to launch billing flow. ${billingResult.toReadableDescription()}")
                }
        }
    }

    private fun queryAllPurchases(
        onPurchaseHistoryCompleted: (List<PurchaseHistoryRecord>) -> Unit,
        onPurchaseHistoryFailed: (BillingError) -> Unit
    ) {
        queryPurchaseHistoryAsync(
            BillingClient.SkuType.SUBS,
            { subsPurchasesList ->
                queryPurchaseHistoryAsync(
                    BillingClient.SkuType.INAPP,
                    { inAppPurchasesList ->
                        onPurchaseHistoryCompleted(
                            subsPurchasesList + inAppPurchasesList
                        )
                    },
                    onPurchaseHistoryFailed
                )
            },
            onPurchaseHistoryFailed
        )
    }

    private fun loadAllProducts(
        products: Set<Product>,
        onSkuDetailsReceive: (List<SkuDetails>) -> Unit,
        onError: (BillingError) -> Unit
    ) {
        val skuInApp = products
            .filter { it.productType == BillingClient.SkuType.INAPP }
            .map { it.productID }
        val skuSubs = products
            .filter { it.productType == BillingClient.SkuType.SUBS }
            .map { it.productID }

        querySkuDetailsAsync(
            BillingClient.SkuType.SUBS,
            skuSubs,
            { skuDetailsSubs ->
                querySkuDetailsAsync(
                    BillingClient.SkuType.INAPP,
                    skuInApp,
                    { skuDetailsInApp ->
                        onSkuDetailsReceive(
                            skuDetailsSubs + skuDetailsInApp
                        )
                    }, onError
                )
            },
            onError
        )
    }

    private fun queryPurchaseHistoryAsync(
        @BillingClient.SkuType skuType: String,
        onPurchaseHistoryReceive: (List<PurchaseHistoryRecord>) -> Unit,
        onError: (BillingError) -> Unit
    ) {
        logger.log("queryPurchaseHistoryAsync - for type $skuType")

        executeRequestOnUIThread { connectionError ->
            if (connectionError == null) {
                withConnectedClient {
                    queryPurchaseHistoryAsync(skuType) { billingResult, purchaseHistoryRecordList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchaseHistoryRecordList != null) {
                            purchaseHistoryRecordList
                                .takeUnless { it.isEmpty() }
                                ?.forEach {
                                    logger.log("queryPurchaseHistoryAsync - purchase history for $skuType is retrieved ${it.toReadableDescription()}")
                                }
                                ?: logger.log("queryPurchaseHistoryAsync - purchase history for $skuType is empty.")

                            onPurchaseHistoryReceive(purchaseHistoryRecordList)
                        } else {
                            var errorMessage = "Error receiving purchase history."
                            if (purchaseHistoryRecordList == null) {
                                errorMessage += " purchaseHistoryRecordList for $skuType is null."
                            }

                            onError(
                                BillingError(
                                    billingResult.responseCode,
                                    "$errorMessage ${billingResult.toReadableDescription()}"
                                )
                            )
                        }
                    }
                }
            } else {
                onError(connectionError)
            }
        }
    }

    private fun querySkuDetailsAsync(
        @BillingClient.SkuType productType: String,
        skuList: List<String>,
        onSkuDetailsReceive: (List<SkuDetails>) -> Unit,
        onError: (BillingError) -> Unit
    ) {
        logger.log("querySkuDetailsAsync - for type $productType, identifiers: ${skuList.joinToString()}")

        executeRequestOnUIThread { connectionError ->
            if (connectionError == null) {
                val params = SkuDetailsParams.newBuilder()
                    .setType(productType)
                    .setSkusList(skuList)
                    .build()

                withConnectedClient {
                    querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                            skuDetailsList
                                .takeUnless { it.isEmpty() }
                                ?.forEach {
                                    logger.log("querySkuDetailsAsync - $it")
                                }
                                ?: logger.log("querySkuDetailsAsync - skuDetailsList for $skuList is empty.")

                            onSkuDetailsReceive(skuDetailsList)
                        } else {
                            var errorMessage = "Error fetching products."
                            if (skuDetailsList == null) {
                                errorMessage += " SkuDetailsList for $skuList is null."
                            }

                            onError(
                                BillingError(
                                    billingResult.responseCode,
                                    "$errorMessage ${billingResult.toReadableDescription()}"
                                )
                            )
                        }
                    }
                }
            } else {
                onError(connectionError)
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

    private fun executeRequestOnUIThread(request: (BillingError?) -> Unit) {
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
            ?: logger.log("Warning: billing client is not ready, BillingClient methods won't work. Stacktrace: ${getStackTrace()}")
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
            purchasesListener.onPurchasesCompleted(purchases)
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

            purchasesListener.onPurchasesFailed(
                purchases ?: emptyList(), BillingError(
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
                                        BillingError(
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


