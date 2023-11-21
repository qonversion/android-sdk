package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductOfferDetails
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.dto.SubscriptionStoreId
import com.qonversion.android.sdk.internal.logger.Logger

internal class ActualBillingClientWrapper(
    billingClientHolder: BillingClientHolder,
    logger: Logger
) : BillingClientWrapperBase(billingClientHolder, logger),
    BillingClientWrapper<SubscriptionStoreId, ProductDetails> {

    private var productDetails = mapOf<String, ProductDetails>()

    override fun withStoreDataLoaded(
        storeIds: List<SubscriptionStoreId>,
        onFailed: (error: BillingError) -> Unit,
        onReady: () -> Unit
    ) {
        val productIds = storeIds.map { it.subscriptionId }

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

    override fun getStoreData(storeId: SubscriptionStoreId): ProductDetails? {
        return productDetails[storeId.subscriptionId]
    }

    override fun makePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        updatePurchaseInfo: UpdatePurchaseInfo?,
        onFailed: (error: BillingError) -> Unit
    ) {
        val storeDetails = product.storeDetails ?: return

        logger.debug("makePurchase() -> Purchasing the product: ${storeDetails.productId}")

        val offerDetails = offerId?.let {
            storeDetails.findOffer(offerId) ?: run {
                onFailed(BillingError(
                    BillingResponseCode.ITEM_UNAVAILABLE,
                    "Failed to find offer $offerId for Qonversion product ${product.qonversionID}"
                ))
                return
            }
        } ?: run {
            if (storeDetails.isInApp) {
                return@run null
            }

            storeDetails.defaultOfferDetails ?: run {
                onFailed(BillingError(
                    BillingResponseCode.ITEM_UNAVAILABLE,
                    "No offer found for purchasing Qonversion subscription product ${product.qonversionID}"
                ))
                return
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

    override fun queryPurchaseHistoryForProduct(
        product: QProduct,
        onCompleted: (BillingResult, PurchaseHistoryRecord?) -> Unit
    ) {
        val storeDetails = product.storeDetails ?: return
        val productType = storeDetails.originalProductDetails.productType

        billingClientHolder.withReadyClient {
            logger.debug(
                "queryPurchaseHistoryForProduct() -> " +
                        "Querying purchase history for ${storeDetails.productId} with type $productType"
            )

            val params = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(productType)
                .build()
            queryPurchaseHistoryAsync(params) { billingResult, purchasesList ->
                onCompleted(
                    billingResult,
                    purchasesList?.firstOrNull { storeDetails.productId == it.productId }
                )
            }
        }
    }

    override fun queryPurchaseHistory(
        productType: QStoreProductType,
        onCompleted: (BillingResult, List<PurchaseHistoryRecord>?) -> Unit
    ) {
        billingClientHolder.withReadyClient {
            val params = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(productType.toProductType())
                .build()
            queryPurchaseHistoryAsync(params, onCompleted)
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
            onSuccess(QStoreProductType.fromSkuType(it.productType))
            return
        }

        loadProducts(listOf(storeId), onFailed) { details ->
            details.firstOrNull()?.takeIf { it.productId == storeId }?.let {
                onSuccess(QStoreProductType.fromSkuType(it.productType))
            } ?: onFailed(
                BillingError(
                    BillingResponseCode.ITEM_UNAVAILABLE,
                    "Product not found"
                )
            )
        }
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
            queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.isOk) {
                    logProductDetails(productDetailsList, productIds)
                    onQuerySkuCompleted(productDetailsList)
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
        productIds: List<String>
    ) {
        productDetailsList
            .takeUnless { it.isEmpty() }
            ?.forEach {
                logger.debug("queryProductDetailsAsync() -> $it")
            }
            ?: logger.release("queryProductDetailsAsync() -> ProductDetails list for $productIds is empty.")
    }

    private fun BillingFlowParams.ProductDetailsParams.Builder.applyOffer(
        offer: QProductOfferDetails?
    ): BillingFlowParams.ProductDetailsParams.Builder {
        offer?.let {
            setOfferToken(offer.offerToken)
        }
        return this
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
}