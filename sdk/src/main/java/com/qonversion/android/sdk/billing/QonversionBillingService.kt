package com.qonversion.android.sdk.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.*
import com.qonversion.android.sdk.logger.Logger
import java.util.concurrent.ConcurrentLinkedQueue

class QonversionBillingService(
    private val application: Application,
    private val mainHandler: Handler,
    private val purchasesListener: PurchasesListener,
    private val logger: Logger
) : PurchasesUpdatedListener, BillingClientStateListener, BillingService {

    private var billingClient: BillingClient? = null

    private val requestsQueue = ConcurrentLinkedQueue<(connectionError: BillingError?) -> Unit>()

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

    override fun queryPurchasesHistory(
        onQueryHistoryCompleted: (purchases: List<PurchaseHistoryRecord>) -> Unit,
        onQueryHistoryFailed: (error: BillingError) -> Unit
    ) {
        queryAllPurchasesHistory(
            { allPurchases ->
                onQueryHistoryCompleted(allPurchases)
            },
            { error ->
                logger.log(error.toString())
                onQueryHistoryFailed(error)
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

    override fun consume(
        purchaseToken: String,
        onConsumeFailed: (error: BillingError) -> Unit
    ) {
        logger.log("Consuming purchase with token $purchaseToken")
        executeOnMainThread { connectionError ->
            if (connectionError == null) {
                val params = ConsumeParams
                    .newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()

                withReadyClient {
                    consumeAsync(
                        params
                    ) { billingResult, purchaseToken ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            val errorMessage =
                                "Failed to consume purchase with token $purchaseToken ${billingResult.getDescription()}"
                            onConsumeFailed(BillingError(billingResult.responseCode, errorMessage))
                            logger.log(errorMessage)
                        }
                    }
                }
            }
        }
    }

    override fun acknowledge(
        purchaseToken: String,
        onAcknowledgeFailed: (error: BillingError) -> Unit
    ) {
        logger.log("Acknowledging purchase with token $purchaseToken")
        executeOnMainThread { connectionError ->
            if (connectionError == null) {
                val params = AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()

                withReadyClient {
                    acknowledgePurchase(
                        params
                    ) { billingResult ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            val errorMessage =
                                "Failed to acknowledge purchase with token $purchaseToken ${billingResult.getDescription()}"
                            onAcknowledgeFailed(
                                BillingError(
                                    billingResult.responseCode,
                                    errorMessage
                                )
                            )
                            logger.log(errorMessage)
                        }
                    }
                }
            }
        }
    }

    override fun queryPurchases(
        onQueryCompleted: (purchases: List<Purchase>) -> Unit,
        onQueryFailed: (error: BillingError) -> Unit
    ) {
        logger.log("queryPurchases - querying for subs and inapp")
        executeOnMainThread { connectionError ->
            if (connectionError == null) {
                withReadyClient {
                    val activeSubs = queryPurchases(BillingClient.SkuType.SUBS)
                    val unconsumedInApp = queryPurchases(BillingClient.SkuType.INAPP)
                    val purchasesResult = mutableListOf<Purchase>()

                    if (activeSubs?.responseCode == BillingClient.BillingResponseCode.OK
                        && unconsumedInApp?.responseCode == BillingClient.BillingResponseCode.OK
                    ) {
                        purchasesResult.addAll(activeSubs.purchasesList ?: emptyList())
                        purchasesResult.addAll(unconsumedInApp.purchasesList ?: emptyList())
                        onQueryCompleted(purchasesResult)

                        purchasesResult
                            .takeUnless { it.isEmpty() }
                            ?.forEach {
                                logger.log("queryPurchases - purchases cache is retrieved ${it.getDescription()}")
                            }
                            ?: logger.log("queryPurchases - purchases cache is empty.")
                    } else {
                        onQueryFailed(
                            BillingError(
                                activeSubs.responseCode,
                                "Failed to handle pending purchase queue"
                            )
                        )
                    }
                }
            } else {
                onQueryFailed(connectionError)
            }
        }
    }

    override fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails?,
        @BillingFlowParams.ProrationMode prorationMode: Int?
    ) {
        if (oldSkuDetails != null) {
            replaceOldPurchase(activity, skuDetails, oldSkuDetails, prorationMode)
        } else {
            makePurchase(
                activity,
                skuDetails
            )
        }
    }

    private fun replaceOldPurchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails,
        @BillingFlowParams.ProrationMode prorationMode: Int?
    ) {
        getPurchaseHistoryFrom(oldSkuDetails)
        { billingResult, oldPurchase ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (oldPurchase != null) {
                    logger.log("Purchase was found successfully for sku: ${oldSkuDetails.sku}")
                    makePurchase(
                        activity,
                        skuDetails,
                        UpdatePurchaseInfo(
                            oldPurchase.sku,
                            oldPurchase.purchaseToken,
                            prorationMode
                        )
                    )
                } else {
                    val errorMessage = "No existing purchase for sku: ${oldSkuDetails.sku}"
                    logger.log(errorMessage)
                    purchasesListener.onPurchasesFailed(
                        null,
                        BillingError(billingResult.responseCode, errorMessage)
                    )
                }
            } else {
                val errorMessage =
                    "Failed to update purchase: ${billingResult.getDescription()}"
                logger.log(errorMessage)
                purchasesListener.onPurchasesFailed(
                    null,
                    BillingError(billingResult.responseCode, errorMessage)
                )
            }
        }
    }

    private fun getPurchaseHistoryFrom(
        skuDetails: SkuDetails,
        completion: (BillingResult, PurchaseHistoryRecord?) -> Unit
    ) {
        withReadyClient {
            logger.log("Querying Purchase with ${skuDetails.sku} and type ${skuDetails.type}")
            queryPurchaseHistoryAsync(skuDetails.type) { billingResult, purchasesList ->
                completion(
                    billingResult,
                    purchasesList?.firstOrNull { skuDetails.sku == it.sku }?.let {
                        it
                    }
                )
            }
        }
    }

    private fun makePurchase(
        activity: Activity,
        skuDetails: SkuDetails,
        updatePurchaseInfo: UpdatePurchaseInfo? = null
    ) {
        logger.log("Purchasing for sku: ${skuDetails.sku}")

        executeOnMainThread { connectionError ->
            if (connectionError == null) {
                val params = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetails)
                    .apply {
                        if (updatePurchaseInfo != null) {
                            setOldSku(updatePurchaseInfo.oldSku, updatePurchaseInfo.purchaseToken)
                            updatePurchaseInfo.prorationMode?.let { prorationMode ->
                                setReplaceSkusProrationMode(prorationMode)
                            }
                        }
                    }.build()

                this@QonversionBillingService.launchBillingFlow(activity, params)
            }
        }
    }

    @UiThread
    private fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams
    ) {
        withReadyClient {
            launchBillingFlow(activity, params)
                .takeIf { billingResult -> billingResult?.responseCode != BillingClient.BillingResponseCode.OK }
                ?.let { billingResult ->
                    logger.log("Failed to launch billing flow. ${billingResult.getDescription()}")
                }
        }
    }

    private fun queryAllPurchasesHistory(
        onReceiveHistoryCompleted: (List<PurchaseHistoryRecord>) -> Unit,
        onReceiveHistoryFailed: (BillingError) -> Unit
    ) {
        queryPurchaseHistoryAsync(
            BillingClient.SkuType.SUBS,
            { subsPurchasesList ->
                queryPurchaseHistoryAsync(
                    BillingClient.SkuType.INAPP,
                    { inAppPurchasesList ->
                        onReceiveHistoryCompleted(
                            subsPurchasesList + inAppPurchasesList
                        )
                    },
                    onReceiveHistoryFailed
                )
            },
            onReceiveHistoryFailed
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

        executeOnMainThread { connectionError ->
            if (connectionError == null) {
                withReadyClient {
                    queryPurchaseHistoryAsync(skuType) { billingResult, purchaseHistory ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchaseHistory != null) {
                            purchaseHistory
                                .takeUnless { it.isEmpty() }
                                ?.forEach {
                                    logger.log("queryPurchaseHistoryAsync - purchase history for $skuType is retrieved ${it.getDescription()}")
                                }
                                ?: logger.log("queryPurchaseHistoryAsync - purchase history for $skuType is empty.")

                            onPurchaseHistoryReceive(purchaseHistory)
                        } else {
                            var errorMessage = "Failed to receive purchase history."
                            if (purchaseHistory == null) {
                                errorMessage += " history Purchases list for $skuType is null."
                            }

                            onError(
                                BillingError(
                                    billingResult.responseCode,
                                    "$errorMessage ${billingResult.getDescription()}"
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
        logger.log("querySkuDetailsAsync - querying for type $productType, identifiers: ${skuList.joinToString()}")

        executeOnMainThread { connectionError ->
            if (connectionError == null) {
                val params = SkuDetailsParams.newBuilder()
                    .setType(productType)
                    .setSkusList(skuList)
                    .build()

                withReadyClient {
                    querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                            skuDetailsList
                                .takeUnless { it.isEmpty() }
                                ?.forEach {
                                    logger.log("querySkuDetailsAsync - $it")
                                }
                                ?: logger.log("querySkuDetailsAsync - SkuDetails list for $skuList is empty.")

                            onSkuDetailsReceive(skuDetailsList)
                        } else {
                            var errorMessage = "Failed to fetch products."
                            if (skuDetailsList == null) {
                                errorMessage += " SkuDetails list for $skuList is null."
                            }

                            onError(
                                BillingError(
                                    billingResult.responseCode,
                                    "$errorMessage ${billingResult.getDescription()}"
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

    private fun executeOnMainThread(request: (BillingError?) -> Unit) {
        synchronized(this@QonversionBillingService) {
            requestsQueue.add(request)
            if (billingClient?.isReady == false) {
                startConnection()
            } else {
                executeRequestsFromQueue()
            }
        }
    }

    private fun executeRequestsFromQueue() {
        synchronized(this@QonversionBillingService) {
            while (billingClient?.isReady == true && !requestsQueue.isEmpty()) {
                requestsQueue.remove()
                    .let {
                        mainHandler.post {
                            it(null)
                        }
                    }
            }
        }
    }

    private fun withReadyClient(receivingFunction: BillingClient.() -> Unit) {
        billingClient
            ?.takeIf { it.isReady }
            ?.let {
                it.receivingFunction()
            }
            ?: logger.log("Connection to the BillingClient was lost")
    }


    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            logger.log("onPurchasesUpdated - purchases updated. ${billingResult.getDescription()} ")
            purchasesListener.onPurchasesCompleted(purchases)
        } else {
            val errorMessage =
                "onPurchasesUpdated - error ${billingResult.getDescription()}"
            logger.log(errorMessage)
            if (!purchases.isNullOrEmpty()) {
                logger.log(
                    "Purchases: " + purchases.joinToString(
                        ", ",
                        transform = { it.getDescription() })
                )
            }

            purchasesListener.onPurchasesFailed(
                purchases ?: emptyList(), BillingError(
                    billingResult.responseCode,
                    errorMessage
                )
            )
        }
    }

    override fun onBillingServiceDisconnected() {
        logger.log("onBillingServiceDisconnected for ${billingClient?.toString()}")
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                logger.log("onBillingSetupFinished successfully for ${billingClient?.toString()}.")
                executeRequestsFromQueue()
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                logger.log("onBillingSetupFinished with error: ${billingResult.getDescription()}")
                synchronized(this@QonversionBillingService) {
                    while (!requestsQueue.isEmpty()) {
                        requestsQueue.remove()
                            .let { billingRequest ->
                                mainHandler.post {
                                    billingRequest(
                                        BillingError(
                                            billingResult.responseCode,
                                            "Billing is not available on this device. ${billingResult.getDescription()}"
                                        )
                                    )
                                }
                            }
                    }
                }
            }
            else -> {
                logger.log("onBillingSetupFinished with error: ${billingResult.getDescription()}")
            }
        }
    }
}