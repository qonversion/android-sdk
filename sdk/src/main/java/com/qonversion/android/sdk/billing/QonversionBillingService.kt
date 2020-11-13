package com.qonversion.android.sdk.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.*
import com.qonversion.android.sdk.entity.PurchaseHistory
import com.qonversion.android.sdk.logger.Logger
import java.util.concurrent.ConcurrentLinkedQueue

internal class QonversionBillingService(
    billingBuilder: BillingBuilder,
    private val mainHandler: Handler,
    private val purchasesListener: PurchasesListener,
    private val logger: Logger
) : PurchasesUpdatedListener, BillingClientStateListener, BillingService {

    private var billingClient: BillingClient? = null
    private val requestsQueue = ConcurrentLinkedQueue<(billingSetupError: BillingError?) -> Unit>()

    init {
        billingClient = billingBuilder.build(this)

        startConnection()
    }

    internal class BillingBuilder(private val context: Application) {
        @UiThread
        fun build(listener: PurchasesUpdatedListener): BillingClient {
            val builder = BillingClient.newBuilder(context)
            builder.enablePendingPurchases()
            builder.setListener(listener)
            return builder.build()
        }
    }

    interface PurchasesListener {
        fun onPurchasesCompleted(purchases: List<Purchase>)
        fun onPurchasesFailed(
            purchases: List<Purchase>,
            error: BillingError
        )
    }

    override fun queryPurchasesHistory(
        onQueryHistoryCompleted: (purchases: List<PurchaseHistory>) -> Unit,
        onQueryHistoryFailed: (error: BillingError) -> Unit
    ) {
        queryAllPurchasesHistory(
            { allPurchases ->
                onQueryHistoryCompleted(allPurchases)
            },
            { error ->
                onQueryHistoryFailed(error)
                logger.log("queryPurchasesHistory() -> $error")
            }
        )
    }

    override fun loadProducts(
        productIDs: Set<String>,
        onLoadCompleted: (products: List<SkuDetails>) -> Unit,
        onLoadFailed: (error: BillingError) -> Unit
    ) {
        loadAllProducts(
            productIDs.toList(),
            { allProducts ->
                onLoadCompleted(allProducts)
            },
            { error ->
                onLoadFailed(error)
                logger.log("loadProducts() -> $error")
            }
        )
    }

    override fun consume(
        purchaseToken: String
    ) {
        logger.log("consume() -> Consuming purchase with token $purchaseToken")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                val params = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()

                withReadyClient {
                    consumeAsync(
                        params
                    ) { billingResult, purchaseToken ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            val errorMessage =
                                "Failed to consume purchase with token $purchaseToken ${billingResult.getDescription()}"
                            logger.log("consume() -> $errorMessage")
                        }
                    }
                }
            }
        }
    }

    override fun acknowledge(
        purchaseToken: String
    ) {
        logger.log("acknowledge() -> Acknowledging purchase with token $purchaseToken")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()

                withReadyClient {
                    acknowledgePurchase(
                        params
                    ) { billingResult ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            val errorMessage =
                                "Failed to acknowledge purchase with token $purchaseToken ${billingResult.getDescription()}"
                            logger.log("acknowledge() -> $errorMessage")
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
        logger.log("queryPurchases() -> Querying purchases from cache for subs and inapp")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
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
                                logger.log("queryPurchases() -> purchases cache is retrieved ${it.getDescription()}")
                            }
                            ?: logger.log("queryPurchases() -> purchases cache is empty.")
                    } else {
                        val errorMessage =
                            "Failed to query purchases from cache ${activeSubs.billingResult.getDescription()}"
                        onQueryFailed(
                            BillingError(
                                activeSubs.responseCode,
                                errorMessage
                            )
                        )
                        logger.log("queryPurchases() -> $errorMessage")
                    }
                }
            } else {
                onQueryFailed(billingSetupError)
            }
        }
    }

    override fun getSkuDetailsFromPurchases(
        purchases: List<Purchase>,
        onCompleted: (List<SkuDetails>) -> Unit,
        onFailed: (BillingError) -> Unit
    ) {
        val skuList = purchases.map { it.sku }
        loadAllProducts(skuList, onCompleted, onFailed)
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
        getPurchaseHistoryFromSkuDetails(oldSkuDetails)
        { billingResult, oldPurchase ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (oldPurchase != null) {
                    logger.log("replaceOldPurchase() -> Purchase was found successfully for sku: ${oldSkuDetails.sku}")
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
                    purchasesListener.onPurchasesFailed(
                        emptyList(),
                        BillingError(billingResult.responseCode, errorMessage)
                    )
                    logger.log("replaceOldPurchase() -> $errorMessage")
                }
            } else {
                val errorMessage =
                    "Failed to update purchase: ${billingResult.getDescription()}"
                purchasesListener.onPurchasesFailed(
                    emptyList(),
                    BillingError(billingResult.responseCode, errorMessage)
                )
                logger.log("replaceOldPurchase() -> $errorMessage")
            }
        }
    }

    private fun getPurchaseHistoryFromSkuDetails(
        skuDetails: SkuDetails,
        onQueryHistoryCompleted: (BillingResult, PurchaseHistoryRecord?) -> Unit
    ) = withReadyClient {
        logger.log("getPurchaseHistoryFromSkuDetails() -> Querying purchase history for ${skuDetails.sku} with type ${skuDetails.type}")

        queryPurchaseHistoryAsync(skuDetails.type) { billingResult, purchasesList ->
            onQueryHistoryCompleted(
                billingResult,
                purchasesList?.firstOrNull { skuDetails.sku == it.sku }
            )
        }
    }

    private fun makePurchase(
        activity: Activity,
        skuDetails: SkuDetails,
        updatePurchaseInfo: UpdatePurchaseInfo? = null
    ) {
        logger.log("makePurchase() -> Purchasing for sku: ${skuDetails.sku}")

        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
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
    ) = withReadyClient {
        launchBillingFlow(activity, params)
            .takeIf { billingResult -> billingResult?.responseCode != BillingClient.BillingResponseCode.OK }
            ?.let { billingResult ->
                logger.log("launchBillingFlow() -> Failed to launch billing flow. ${billingResult.getDescription()}")
            }
    }

    private fun queryAllPurchasesHistory(
        onQueryHistoryCompleted: (List<PurchaseHistory>) -> Unit,
        onQueryHistoryFailed: (BillingError) -> Unit
    ) {
        queryPurchaseHistoryAsync(
            BillingClient.SkuType.SUBS,
            { subsPurchasesList ->
                queryPurchaseHistoryAsync(
                    BillingClient.SkuType.INAPP,
                    { inAppPurchasesList ->
                        onQueryHistoryCompleted(
                            subsPurchasesList + inAppPurchasesList
                        )
                    },
                    onQueryHistoryFailed
                )
            },
            onQueryHistoryFailed
        )
    }

    private fun queryPurchaseHistoryAsync(
        @BillingClient.SkuType skuType: String,
        onQueryHistoryCompleted: (List<PurchaseHistory>) -> Unit,
        onQueryHistoryFailed: (BillingError) -> Unit
    ) {
        logger.log("queryPurchaseHistoryAsync() -> Querying purchase history for type $skuType")

        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                withReadyClient {
                    queryPurchaseHistoryAsync(skuType) { billingResult, purchaseHistoryRecords ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchaseHistoryRecords != null) {
                            val purchaseHistory = getPurchaseHistoryFromHistoryRecords(
                                skuType,
                                purchaseHistoryRecords
                            )
                            onQueryHistoryCompleted(purchaseHistory)
                        } else {
                            var errorMessage = "Failed to retrieve purchase history. "
                            if (purchaseHistoryRecords == null) {
                                errorMessage += "Purchase history for $skuType is null. "
                            }

                            onQueryHistoryFailed(
                                BillingError(
                                    billingResult.responseCode,
                                    "$errorMessage ${billingResult.getDescription()}"
                                )
                            )
                        }
                    }
                }
            } else {
                onQueryHistoryFailed(billingSetupError)
            }
        }
    }

    private fun getPurchaseHistoryFromHistoryRecords(
        @BillingClient.SkuType skuType: String,
        historyRecords: List<PurchaseHistoryRecord>
    ): List<PurchaseHistory> {
        val purchaseHistory = mutableListOf<PurchaseHistory>()
        historyRecords
            .takeUnless { it.isEmpty() }
            ?.forEach { record ->
                purchaseHistory.add(PurchaseHistory(skuType, record))
                logger.log("queryPurchaseHistoryAsync() -> purchase history for $skuType is retrieved ${record.getDescription()}")
            }
            ?: logger.log("queryPurchaseHistoryAsync() -> purchase history for $skuType is empty.")

        return purchaseHistory
    }

    private fun loadAllProducts(
        productIDs: List<String>,
        onQuerySkuCompleted: (List<SkuDetails>) -> Unit,
        onQuerySkuFailed: (BillingError) -> Unit
    ) {
        querySkuDetailsAsync(
            BillingClient.SkuType.SUBS,
            productIDs,
            { skuDetailsSubs ->
                val skuSubs = skuDetailsSubs.map { it.sku }
                val skuInApp = productIDs - skuSubs

                if (skuInApp.isNotEmpty()) {
                    querySkuDetailsAsync(
                        BillingClient.SkuType.INAPP,
                        skuInApp,
                        { skuDetailsInApp ->
                            onQuerySkuCompleted(skuDetailsSubs + skuDetailsInApp)
                        },
                        onQuerySkuFailed
                    )
                } else {
                    onQuerySkuCompleted(skuDetailsSubs)
                }
            },
            onQuerySkuFailed
        )
    }

    private fun querySkuDetailsAsync(
        @BillingClient.SkuType productType: String,
        skuList: List<String>,
        onQuerySkuCompleted: (List<SkuDetails>) -> Unit,
        onQuerySkuFailed: (BillingError) -> Unit
    ) {
        logger.log("querySkuDetailsAsync() -> Querying skuDetails for type $productType, identifiers: ${skuList.joinToString()}")

        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                val params = buildSkuDetailsParams(productType, skuList)

                withReadyClient {
                    querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                            logSkuDetails(skuDetailsList, skuList)
                            onQuerySkuCompleted(skuDetailsList)
                        } else {
                            var errorMessage = "Failed to fetch products. "
                            if (skuDetailsList == null) {
                                errorMessage += "SkuDetails list for $skuList is null. "
                            }

                            onQuerySkuFailed(
                                BillingError(
                                    billingResult.responseCode,
                                    "$errorMessage ${billingResult.getDescription()}"
                                )
                            )
                        }
                    }
                }
            } else {
                onQuerySkuFailed(billingSetupError)
            }
        }
    }

    private fun buildSkuDetailsParams(
        @BillingClient.SkuType productType: String,
        skuList: List<String>
    ): SkuDetailsParams{
        return SkuDetailsParams.newBuilder()
            .setType(productType)
            .setSkusList(skuList)
            .build()
    }

    private fun logSkuDetails(
        skuDetailsList: List<SkuDetails>,
        skuList: List<String>
    ) {
        skuDetailsList
            .takeUnless { it.isEmpty() }
            ?.forEach {
                logger.log("querySkuDetailsAsync() -> $it")
            }
            ?: logger.log("querySkuDetailsAsync() -> SkuDetails list for $skuList is empty.")
    }

    private fun startConnection() {
        mainHandler.post {
            synchronized(this@QonversionBillingService) {
                billingClient?.let {
                    it.startConnection(this)
                    logger.log("startConnection() -> for $it")
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
            while (billingClient?.isReady == true && requestsQueue.isNotEmpty()) {
                requestsQueue.remove()
                    .let {
                        mainHandler.post {
                            it(null)
                        }
                    }
            }
        }
    }

    private fun withReadyClient(billingFunction: BillingClient.() -> Unit) {
        billingClient
            ?.takeIf { it.isReady }
            ?.let {
                it.billingFunction()
            }
            ?: logger.log("Connection to the BillingClient was lost")
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            logger.log("onPurchasesUpdated() -> purchases updated. ${billingResult.getDescription()} ")
            purchasesListener.onPurchasesCompleted(purchases)
        } else {
            val errorMessage = billingResult.getDescription()
            purchasesListener.onPurchasesFailed(
                purchases ?: emptyList(), BillingError(
                    billingResult.responseCode,
                    errorMessage
                )
            )

            logger.log("onPurchasesUpdated() -> failed to update purchases $errorMessage")
            if (!purchases.isNullOrEmpty()) {
                logger.log(
                    "Purchases: " + purchases.joinToString(
                        ", ",
                        transform = { it.getDescription() })
                )
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        logger.log("onBillingServiceDisconnected() -> for ${billingClient?.toString()}")
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                logger.log("onBillingSetupFinished() -> successfully for ${billingClient?.toString()}.")
                executeRequestsFromQueue()
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                logger.log("onBillingSetupFinished() -> with error: ${billingResult.getDescription()}")
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