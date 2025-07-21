package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import android.os.Handler
import com.android.billingclient.api.*
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.dto.ProductStoreId
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseOptionsInternalEnriched
import com.qonversion.android.sdk.internal.logger.Logger
import java.util.concurrent.ConcurrentLinkedQueue

internal class QonversionBillingService internal constructor(
    private val mainHandler: Handler,
    private val purchasesListener: PurchasesListener,
    private val logger: Logger,
    private val isAnalyticsMode: Boolean,
    private val billingClientHolder: BillingClientHolder,
    private val billingClientWrapper: BillingClientWrapper
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
                .map { ProductStoreId(
                    it.storeID!!,
                    it.basePlanID
                ) }
            billingClientWrapper.withStoreDataLoaded(
                actualStoreIds,
                onFailed,
            ) {
                enrichStoreData(products)
                onEnriched(products)
            }
        }

        executeOnMainThread { billingSetupError ->
            if (billingSetupError != null) {
                logger.error("enrichStoreDataAsync() -> $billingSetupError")
                onFailed(billingSetupError)
                return@executeOnMainThread
            }

            fetchProductDetails()
        }
    }

    override fun enrichStoreData(products: List<QProduct>) {
        products.forEach { product ->
            product.storeID?.let { storeId ->
                val productStoreId = ProductStoreId(
                    storeId,
                    product.basePlanID
                )
                billingClientWrapper.getStoreData(productStoreId)?.let { storeData ->
                    product.setStoreProductDetails(storeData)
                }
            }
        }
    }

    override fun purchase(activity: Activity, purchaseOptions: PurchaseOptionsInternalEnriched) {
        fun handlePurchase() {
            if (purchaseOptions.oldProduct?.storeDetails != null) {
                updatePurchase(
                    activity,
                    purchaseOptions.product,
                    purchaseOptions.options?.offerId,
                    purchaseOptions.options?.applyOffer,
                    purchaseOptions.oldProduct,
                    purchaseOptions.updatePolicy)
            } else {
                makePurchase(
                    activity,
                    purchaseOptions.product,
                    purchaseOptions.options?.offerId,
                    purchaseOptions.options?.applyOffer
                )
            }
        }

        if (purchaseOptions.product.storeDetails != null) {
            handlePurchase()
        } else {
            enrichStoreDataAsync(
                listOfNotNull(purchaseOptions.product, purchaseOptions.oldProduct),
                { error -> purchasesListener.onPurchasesFailed(error) }
            ) {
                handlePurchase()
            }
        }
    }

    override fun consumePurchases(purchases: List<Purchase>) {
        if (isAnalyticsMode) {
            return
        }

        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase ->
                val productId = purchase.productId ?: return
                getStoreProductType(
                    productId,
                    { error -> logger.error("Failed to fetch product type for purchase $productId - " + error.message) }
                ) { productType ->
                    when (productType) {
                        QStoreProductType.InApp -> {
                            consume(purchase.purchaseToken)
                        }
                        QStoreProductType.Subscription -> {
                            if (!purchase.isAcknowledged) {
                                acknowledge(purchase.purchaseToken)
                            }
                        }
                    }
                }
            }
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

            billingClientWrapper.queryPurchases(onFailed, onCompleted)
        }
    }

    override fun getStoreProductType(
        storeId: String,
        onFailed: (error: BillingError) -> Unit,
        onSuccess: (type: QStoreProductType) -> Unit
    ) {
        billingClientWrapper.getStoreProductType(
            storeId,
            onFailed,
            onSuccess
        )
    }

    private fun updatePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        applyOffer: Boolean?,
        oldProduct: QProduct,
        updatePolicy: QPurchaseUpdatePolicy?
    ) {
        product.storeDetails ?: run {
            purchasesListener.onPurchasesFailed(
                BillingError(
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    "Store details for purchasing Qonversion product " +
                            "${product.qonversionID} were not found"
                )
            )
            return@updatePurchase
        }

        billingClientWrapper.queryPurchaseForProduct(oldProduct) { billingResult, purchase ->
            if (!billingResult.isOk) {
                val errorMessage = "Failed to update purchase: ${billingResult.getDescription()}"
                purchasesListener.onPurchasesFailed(
                    BillingError(billingResult.responseCode, errorMessage)
                )
                logger.error("updatePurchase() -> $errorMessage")
                return@queryPurchaseForProduct
            }

            if (purchase != null) {
                logger.debug(
                    "updatePurchase() -> Purchase was found successfully for store product: ${purchase.productId}"
                )

                makePurchase(
                    activity,
                    product,
                    offerId,
                    applyOffer,
                    UpdatePurchaseInfo(purchase.purchaseToken, updatePolicy)
                )
            } else {
                val errorMessage = "No existing purchase for Qonversion product: ${oldProduct.qonversionID}"
                purchasesListener.onPurchasesFailed(
                    BillingError(billingResult.responseCode, errorMessage)
                )
                logger.error("updatePurchase() -> $errorMessage")
            }
        }
    }

    private fun makePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        applyOffer: Boolean?,
        updatePurchaseInfo: UpdatePurchaseInfo? = null
    ) {
        executeOnMainThread { billingSetupError ->
            if (billingSetupError != null) {
                return@executeOnMainThread
            }

            product.storeDetails ?: run {
                purchasesListener.onPurchasesFailed(
                    BillingError(
                        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
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
                applyOffer,
                updatePurchaseInfo,
            ) { error -> purchasesListener.onPurchasesFailed(error) }
        }
    }

    private fun consume(purchaseToken: String) {
        logger.debug("consume() -> Consuming purchase with token $purchaseToken")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                billingClientWrapper.consume(purchaseToken)
            }
        }
    }

    private fun acknowledge(
        purchaseToken: String
    ) {
        logger.debug("acknowledge() -> Acknowledging purchase with token $purchaseToken")
        executeOnMainThread { billingSetupError ->
            if (billingSetupError == null) {
                billingClientWrapper.acknowledge(purchaseToken)
            }
        }
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

            logger.error("onPurchasesUpdated() -> failed to update purchases $errorMessage")
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
}
