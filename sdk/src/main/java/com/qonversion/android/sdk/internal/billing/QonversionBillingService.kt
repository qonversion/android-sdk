package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import android.os.Handler
import com.android.billingclient.api.*
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.dto.SubscriptionStoreId
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory
import com.qonversion.android.sdk.internal.logger.Logger
import java.util.concurrent.ConcurrentLinkedQueue

internal class QonversionBillingService internal constructor(
    private val mainHandler: Handler,
    private val purchasesListener: PurchasesListener,
    private val logger: Logger,
    private val isAnalyticsMode: Boolean,
    private val billingClientHolder: BillingClientHolder,
    private val legacyBillingClientWrapper: LegacyBillingClientWrapper,
    private val actualBillingClientWrapper: ActualBillingClientWrapper
) : PurchasesUpdatedListener, BillingClientHolder.ConnectionListener, BillingService {

    private val requestsQueue = ConcurrentLinkedQueue<(billingSetupError: BillingError?) -> Unit>()

    interface PurchasesListener {
        fun onPurchasesCompleted(purchases: List<Purchase>)
        fun onPurchasesFailed(
            error: BillingError,
            purchases: List<Purchase> = emptyList()
        )
    }

    init {
        billingClientHolder.subscribeOnPurchasesUpdates(this)
    }

    override fun enrichStoreDataAsync(
        products: List<QProduct>,
        onFailed: (error: BillingError) -> Unit,
        onEnriched: (products: List<QProduct>) -> Unit
    ) {
        if (!products.any { it.storeID != null }) {
            onEnriched(products)
            return
        }

        fun fetchProductDetails() {
            // Fetching ProductDetails
            val actualStoreIds = products.filter { it.storeID != null }
                .map { SubscriptionStoreId(
                    it.storeID!!,
                    it.basePlanID
                ) }
            actualBillingClientWrapper.withStoreDataLoaded(
                actualStoreIds,
                onFailed,
            ) {
                enrichStoreData(products)
                onEnriched(products)
            }
        }

        executeOnMainThread { billingSetupError ->
            if (billingSetupError != null) {
                logger.release("enrichStoreDataAsync() -> $billingSetupError")
                onFailed(billingSetupError)
                return@executeOnMainThread
            }

            // Fetching legacy SkuDetails
            val legacyStoreIds = products.mapNotNull { it.storeID }
            legacyBillingClientWrapper.withStoreDataLoaded(
                legacyStoreIds,
                { fetchProductDetails() },
            ) {
                fetchProductDetails()
            }
        }
    }

    override fun enrichStoreData(products: List<QProduct>) {
        products.forEach { product ->
            product.storeID?.let { storeId ->
                @Suppress("DEPRECATION")
                product.skuDetail = legacyBillingClientWrapper.getStoreData(storeId)

                val subscriptionStoreId = SubscriptionStoreId(
                    storeId,
                    product.basePlanID
                )
                actualBillingClientWrapper.getStoreData(subscriptionStoreId)?.let { storeData ->
                    product.setStoreProductDetails(storeData)
                }
            }
        }
    }

    override fun purchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        oldProduct: QProduct?,
        updatePolicy: QPurchaseUpdatePolicy?
    ) {
        fun handlePurchase() {
            if (oldProduct != null && oldProduct.hasAnyStoreDetails) {
                updatePurchase(activity, product, offerId, oldProduct, updatePolicy)
            } else {
                makePurchase(activity, product, offerId)
            }
        }

        if (product.hasAnyStoreDetails) {
            handlePurchase()
        } else {
            enrichStoreDataAsync(
                listOfNotNull(product, oldProduct),
                { error -> purchasesListener.onPurchasesFailed(error) }
            ) {
                handlePurchase()
            }
        }
    }

    override fun consumePurchases(purchases: List<Purchase>, products: List<QProduct>) {
        if (isAnalyticsMode) {
            return
        }

        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase ->
                val productId = purchase.productId ?: return
                getStoreProductType(
                    productId,
                    { error -> logger.release("Failed to fetch product type for purchase $productId - " + error.message) }
                ) { productType ->
                    when (productType) {
                        QStoreProductType.InApp -> {
                            consume(purchase.purchaseToken)
                        }
                        QStoreProductType.Subscription -> {
                            if (!purchase.isAcknowledged) {
                                acknowledge(purchase.purchaseToken)
                            }
                        }}
                }
            }
    }

    override fun consumeHistoryRecords(historyRecords: List<PurchaseHistory>) {
        if (isAnalyticsMode) {
            return
        }

        historyRecords.forEach { record ->
            when (record.type) {
                QStoreProductType.InApp -> consume(record.historyRecord.purchaseToken)
                QStoreProductType.Subscription -> acknowledge(record.historyRecord.purchaseToken)
            }
        }
    }

    override fun queryPurchasesHistory(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<PurchaseHistory>) -> Unit
    ) {
        fun fireOnFailed(error: BillingError) {
            onFailed(error)
            logger.release("queryPurchasesHistory() -> $error")
        }

        queryPurchaseHistoryAsync(
            QStoreProductType.Subscription,
            { subsPurchasesList ->
                queryPurchaseHistoryAsync(
                    QStoreProductType.InApp,
                    { inAppPurchasesList ->
                        onCompleted(
                            subsPurchasesList + inAppPurchasesList
                        )
                    },
                    { error -> fireOnFailed(error) }
                )
            },
            { error -> fireOnFailed(error) }
        )
    }

    override fun queryPurchases(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<Purchase>) -> Unit
    ) {
        logger.debug("queryPurchases() -> Querying purchases from cache for subs and inapp")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError != null) {
                onFailed(billingSetupError)
                return@executeOnMainThread
            }

            actualBillingClientWrapper.queryPurchases(onFailed, onCompleted)
        }
    }

    override fun getStoreProductType(
        storeId: String,
        onFailed: (error: BillingError) -> Unit,
        onSuccess: (type: QStoreProductType) -> Unit
    ) {
        actualBillingClientWrapper.getStoreProductType(
            storeId,
            { actualError ->
                legacyBillingClientWrapper.getStoreProductType(
                    storeId,
                    { onFailed(actualError) },
                    onSuccess
                )
            },
            onSuccess
        )
    }

    private fun updatePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        oldProduct: QProduct,
        updatePolicy: QPurchaseUpdatePolicy?
    ) {
        val billingClientWrapper = chooseBillingClientWrapperForProductPurchase(product) ?: return

        billingClientWrapper.queryPurchaseHistoryForProduct(oldProduct) { billingResult, purchaseHistoryRecord ->
            if (!billingResult.isOk) {
                val errorMessage = "Failed to update purchase: ${billingResult.getDescription()}"
                purchasesListener.onPurchasesFailed(
                    BillingError(billingResult.responseCode, errorMessage)
                )
                logger.release("updatePurchase() -> $errorMessage")
                return@queryPurchaseHistoryForProduct
            }

            if (purchaseHistoryRecord != null) {
                logger.debug(
                    "updatePurchase() -> Purchase was found successfully for store product: ${purchaseHistoryRecord.productId}"
                )

                makePurchase(
                    activity,
                    product,
                    offerId,
                    UpdatePurchaseInfo(purchaseHistoryRecord.purchaseToken, updatePolicy)
                )
            } else {
                val errorMessage = "No existing purchase for Qonversion product: ${oldProduct.qonversionID}"
                purchasesListener.onPurchasesFailed(
                    BillingError(billingResult.responseCode, errorMessage)
                )
                logger.release("updatePurchase() -> $errorMessage")
            }
        }
    }

    private fun makePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        updatePurchaseInfo: UpdatePurchaseInfo? = null
    ) {
        executeOnMainThread { billingSetupError ->
            if (billingSetupError != null) {
                return@executeOnMainThread
            }

            val billingClientWrapper = chooseBillingClientWrapperForProductPurchase(product) ?: run {
                purchasesListener.onPurchasesFailed(
                    BillingError(
                        BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
                        "Store details for purchasing Qonversion product " +
                                "${product.qonversionID} were not found"
                    )
                )
                return@executeOnMainThread
            }

            billingClientWrapper.makePurchase(
                activity,
                product,
                offerId,
                updatePurchaseInfo,
            ) { error -> purchasesListener.onPurchasesFailed(error) }
        }
    }

    private fun consume(purchaseToken: String) {
        logger.debug("consume() -> Consuming purchase with token $purchaseToken")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                actualBillingClientWrapper.consume(purchaseToken)
            }
        }
    }

    private fun acknowledge(
        purchaseToken: String
    ) {
        logger.debug("acknowledge() -> Acknowledging purchase with token $purchaseToken")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                actualBillingClientWrapper.acknowledge(purchaseToken)
            }
        }
    }

    private fun queryPurchaseHistoryAsync(
        productType: QStoreProductType,
        onQueryHistoryCompleted: (List<PurchaseHistory>) -> Unit,
        onQueryHistoryFailed: (BillingError) -> Unit
    ) {
        logger.debug("queryPurchaseHistoryAsync() -> Querying purchase history for type $QStoreProductType")

        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                actualBillingClientWrapper.queryPurchaseHistory(productType) { billingResult, purchaseHistoryRecords ->
                    if (billingResult.isOk && purchaseHistoryRecords != null) {
                        val purchaseHistory = getPurchaseHistoryFromHistoryRecords(
                            productType,
                            purchaseHistoryRecords
                        )
                        onQueryHistoryCompleted(purchaseHistory)
                    } else {
                        var errorMessage = "Failed to retrieve purchase history. "
                        if (purchaseHistoryRecords == null) {
                            errorMessage += "Purchase history for $productType is null. "
                        }

                        onQueryHistoryFailed(
                            BillingError(
                                billingResult.responseCode,
                                "$errorMessage ${billingResult.getDescription()}"
                            )
                        )
                    }
                }
            } else {
                onQueryHistoryFailed(billingSetupError)
            }
        }
    }

    private fun getPurchaseHistoryFromHistoryRecords(
        productType: QStoreProductType,
        historyRecords: List<PurchaseHistoryRecord>
    ): List<PurchaseHistory> {
        val purchaseHistory = mutableListOf<PurchaseHistory>()
        historyRecords
            .takeUnless { it.isEmpty() }
            ?.forEach { record ->
                purchaseHistory.add(PurchaseHistory(productType, record))
                logger.debug("queryPurchaseHistoryAsync() -> purchase history for $productType is retrieved ${record.getDescription()}")
            }
            ?: logger.release("queryPurchaseHistoryAsync() -> purchase history for $productType is empty.")

        return purchaseHistory
    }

    private fun executeOnMainThread(request: (BillingError?) -> Unit) {
        synchronized(this@QonversionBillingService) {
            requestsQueue.add(request)
            if (!billingClientHolder.isConnected) {
                billingClientHolder.startConnection(this)
            } else {
                executeRequestsFromQueue()
            }
        }
    }

    private fun executeRequestsFromQueue() {
        synchronized(this@QonversionBillingService) {
            while (billingClientHolder.isConnected && requestsQueue.isNotEmpty()) {
                requestsQueue.remove()
                    .let {
                        mainHandler.post {
                            it(null)
                        }
                    }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.isOk && purchases != null) {
            logger.debug("onPurchasesUpdated() -> purchases updated. ${billingResult.getDescription()} ")
            purchasesListener.onPurchasesCompleted(purchases)
        } else {
            val errorMessage = billingResult.getDescription()
            purchasesListener.onPurchasesFailed(
                BillingError(
                    billingResult.responseCode,
                    errorMessage
                ),
                purchases ?: emptyList()
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

    override fun onBillingClientConnected() {
        executeRequestsFromQueue()
    }

    override fun onBillingClientUnavailable(error: BillingError) {
        synchronized(this@QonversionBillingService) {
            while (!requestsQueue.isEmpty()) {
                requestsQueue.remove().let { billingRequest ->
                    mainHandler.post { billingRequest(error) }
                }
            }
        }
    }

    private fun chooseBillingClientWrapperForProductPurchase(
        product: QProduct
    ): BillingClientWrapper<*, *>? {
        // Use new billing for the products, where
        // -- storeDetails are loaded
        // -- base plan id is specified
        // -- offer for that base plan exists
        val storeDetails = product.storeDetails
        return when {
            storeDetails != null && (product.basePlanID != null || storeDetails.isInApp) -> actualBillingClientWrapper
            @Suppress("DEPRECATION") product.skuDetail != null -> legacyBillingClientWrapper
            else -> return null
        }
    }
}
