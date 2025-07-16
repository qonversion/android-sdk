package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import androidx.annotation.UiThread
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductOfferDetails
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.dto.ProductStoreId
import com.qonversion.android.sdk.internal.logger.Logger

internal class BillingClientWrapper(
    private val billingClientHolder: BillingClientHolder,
    private val logger: Logger
) : IBillingClientWrapper<ProductStoreId, ProductDetails> {

    private var productDetails = mapOf<String, ProductDetails>()

    override fun withStoreDataLoaded(
        storeIds: List<ProductStoreId>,
        onFailed: (error: BillingError) -> Unit,
        onReady: () -> Unit
    ) {
        val productIds = storeIds.map { it.productId }

        val idsToLoad = productIds.filterNot { productDetails.containsKey(it) }
        if (idsToLoad.isEmpty()) {
            onReady()
            return
        }

        loadProducts(idsToLoad, onFailed) { details ->
            val productDetailsMap = details.associateBy { it.productId }
            productDetails = productDetails + productDetailsMap.toMutableMap()

            onReady()
        }
    }

    override fun getStoreData(storeId: ProductStoreId): ProductDetails? {
        return productDetails[storeId.productId]
    }

    override fun makePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        applyOffer: Boolean?,
        updatePurchaseInfo: UpdatePurchaseInfo?,
        onFailed: (error: BillingError) -> Unit
    ) {
        fun fireError(message: String) {
            onFailed(BillingError(BillingResponseCode.ITEM_UNAVAILABLE, message))
        }

        val storeDetails = product.storeDetails ?: run {
            onFailed(
                BillingError(
                    BillingResponseCode.ITEM_UNAVAILABLE,
                    "Store details not found for purchase"
                )
            )
            return
        }

        logger.debug("makePurchase() -> Purchasing the product: ${storeDetails.productId}")

        val offerDetails: QProductOfferDetails? = when {
            storeDetails.isInApp -> null
            applyOffer == false -> {
                storeDetails.basePlanSubscriptionOfferDetails ?: run {
                    fireError("Failed to find base plan offer for Qonversion product ${product.qonversionID}")
                    return
                }
            }
            offerId?.isNotEmpty() == true -> {
                storeDetails.findOffer(offerId) ?: run {
                    fireError("Failed to find offer $offerId for Qonversion product ${product.qonversionID}")
                    return
                }
            }
            else -> {
                storeDetails.defaultSubscriptionOfferDetails ?: run {
                    fireError("No offer found for purchasing Qonversion subscription product ${product.qonversionID}")
                    return
                }
            }
        }

        val productDetailsParamList = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(storeDetails.originalProductDetails)
            .applyOffer(offerDetails)
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamList))
            .setSubscriptionUpdateParams(updatePurchaseInfo)
            .build()

        launchBillingFlow(activity, params)
    }

    override fun queryPurchaseForProduct(
        product: QProduct,
        onCompleted: (BillingResult, Purchase?) -> Unit
    ) {
        val storeDetails = product.storeDetails ?: return
        val productType = storeDetails.originalProductDetails.productType

        billingClientHolder.withReadyClient {
            logger.debug(
                "queryPurchaseHistoryForProduct() -> " +
                        "Querying purchase history for ${storeDetails.productId} with type $productType"
            )

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            queryPurchasesAsync(params) { billingResult, purchasesList ->
                onCompleted(
                    billingResult,
                    purchasesList.firstOrNull { storeDetails.productId == it.productId }
                )
            }
        }
    }

    override fun queryPurchases(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<Purchase>) -> Unit
    ) {
        billingClientHolder.withReadyClient {
            val subscriptionParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            queryPurchasesAsync(subscriptionParams) querySubscriptions@{ subsResult, activeSubs ->
                if (!subsResult.isOk) {
                    handlePurchasesQueryError(subsResult, "subscription", onFailed)
                    return@querySubscriptions
                }

                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                queryPurchasesAsync(inAppParams) queryInAppPurchases@{ inAppsResult, unconsumedInApp ->
                    if (!inAppsResult.isOk) {
                        handlePurchasesQueryError(subsResult, "in-app", onFailed)
                        return@queryInAppPurchases
                    }

                    val purchasesResult = activeSubs + unconsumedInApp
                    onCompleted(purchasesResult)

                    purchasesResult
                        .takeUnless { it.isEmpty() }
                        ?.forEach {
                            logger.debug("queryPurchases() -> purchases cache is retrieved ${it.getDescription()}")
                        }
                        ?: logger.release("queryPurchases() -> purchases cache is empty.")
                }
            }
        }
    }

    override fun getStoreProductType(
        storeId: String,
        onFailed: (error: BillingError) -> Unit,
        onSuccess: (type: QStoreProductType) -> Unit
    ) {
        productDetails[storeId]?.let {
            onSuccess(QStoreProductType.fromProductType(it.productType))
            return
        }

        loadProducts(listOf(storeId), onFailed) { details ->
            details.firstOrNull()?.takeIf { it.productId == storeId }?.let {
                onSuccess(QStoreProductType.fromProductType(it.productType))
            } ?: onFailed(
                BillingError(
                    BillingResponseCode.ITEM_UNAVAILABLE,
                    "Product not found"
                )
            )
        }
    }

    override fun consume(purchaseToken: String) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClientHolder.withReadyClient {
            consumeAsync(
                params
            ) { billingResult, purchaseToken ->
                if (!billingResult.isOk) {
                    val errorMessage =
                        "Failed to consume purchase with token $purchaseToken ${billingResult.getDescription()}"
                    logger.debug("consume() -> $errorMessage")
                }
            }
        }
    }

    override fun acknowledge(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClientHolder.withReadyClient {
            acknowledgePurchase(
                params
            ) { billingResult ->
                if (!billingResult.isOk) {
                    val errorMessage =
                        "Failed to acknowledge purchase with token $purchaseToken ${billingResult.getDescription()}"
                    logger.debug("acknowledge() -> $errorMessage")
                }
            }
        }
    }

    @UiThread
    private fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams
    ) = billingClientHolder.withReadyClient {
        launchBillingFlow(activity, params)
            .takeUnless { billingResult -> billingResult.isOk }
            ?.let { billingResult ->
                logger.error("launchBillingFlow() -> Failed to launch billing flow. ${billingResult.getDescription()}")
            }
    }

    private fun handlePurchasesQueryError(
        billingResult: BillingResult,
        purchaseType: String,
        onQueryFailed: (error: BillingError) -> Unit
    ) {
        val errorMessage =
            "Failed to query $purchaseType purchases from cache: ${billingResult.getDescription()}"
        onQueryFailed(BillingError(billingResult.responseCode, errorMessage))
        logger.error("queryPurchases() -> $errorMessage")
    }

    private fun BillingFlowParams.Builder.setSubscriptionUpdateParams(
        info: UpdatePurchaseInfo? = null
    ): BillingFlowParams.Builder {
        if (info != null) {
            val updateParamsBuilder = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
            updateParamsBuilder.setOldPurchaseToken(info.purchaseToken)
            val updateParams = updateParamsBuilder.apply {
                info.updatePolicy?.toReplacementMode()?.let {
                    setSubscriptionReplacementMode(it)
                }
            }.build()

            setSubscriptionUpdateParams(updateParams)
        }

        return this
    }

    private fun loadProducts(
        productIds: List<String>,
        onFailed: (BillingError) -> Unit,
        onCompleted: (List<ProductDetails>) -> Unit
    ) {
        queryProductDetailsAsync(
            BillingClient.ProductType.SUBS,
            productIds,
            { subscriptionProductDetails ->
                val subscriptionProductIds = subscriptionProductDetails.map { it.productId }.toSet()
                val inAppProductIds = productIds - subscriptionProductIds

                if (inAppProductIds.isNotEmpty()) {
                    queryProductDetailsAsync(
                        BillingClient.ProductType.INAPP,
                        inAppProductIds,
                        { inAppProductDetails ->
                            onCompleted(subscriptionProductDetails + inAppProductDetails)
                        },
                        onFailed
                    )
                } else {
                    onCompleted(subscriptionProductDetails)
                }
            },
            onFailed
        )
    }

    private fun queryProductDetailsAsync(
        productType: String,
        productIds: List<String>,
        onQuerySkuCompleted: (List<ProductDetails>) -> Unit,
        onQuerySkuFailed: (BillingError) -> Unit
    ) {
        val productDetails = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        }
        val params = QueryProductDetailsParams
            .newBuilder()
            .setProductList(productDetails)
            .build()

        billingClientHolder.withReadyClient {
            queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                if (billingResult.isOk) {
                    logProductDetails(productDetailsResult.productDetailsList, productDetailsResult.unfetchedProductList, productIds)
                    onQuerySkuCompleted(productDetailsResult.productDetailsList)
                } else {
                    onQuerySkuFailed(
                        BillingError(
                            billingResult.responseCode,
                            "Failed to fetch products. ${billingResult.getDescription()}"
                        )
                    )
                }
            }
        }
    }

    private fun logProductDetails(
        productDetailsList: List<ProductDetails>,
        unfetchedProductList: List<UnfetchedProduct>,
        productIds: List<String>
    ) {
        productDetailsList
            .takeUnless { it.isEmpty() }
            ?.forEach {
                logger.debug("queryProductDetailsAsync() -> $it")
            }
            ?: logger.warn("queryProductDetailsAsync() -> ProductDetails list for $productIds is empty.")
        unfetchedProductList
            .takeUnless { it.isEmpty() }
            ?.forEach {
                logger.warn("queryProductDetailsAsync() -> Unfetched product: $it")
            }
    }

    private fun BillingFlowParams.ProductDetailsParams.Builder.applyOffer(
        offer: QProductOfferDetails?
    ): BillingFlowParams.ProductDetailsParams.Builder {
        offer?.let {
            setOfferToken(offer.offerToken)
        }
        return this
    }
}
