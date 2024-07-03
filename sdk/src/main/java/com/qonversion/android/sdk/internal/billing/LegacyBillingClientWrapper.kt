package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.*
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.logger.Logger

typealias LegacyStoreId = String

internal class LegacyBillingClientWrapper(
    billingClientHolder: BillingClientHolder,
    logger: Logger,
) : BillingClientWrapperBase(billingClientHolder, logger),
    @Suppress("DEPRECATION") IBillingClientWrapper<LegacyStoreId, SkuDetails> {

    @Suppress("DEPRECATION")
    private var skuDetails = mapOf<LegacyStoreId, SkuDetails>()

    override fun withStoreDataLoaded(
        storeIds: List<LegacyStoreId>,
        onFailed: (error: BillingError) -> Unit,
        onReady: () -> Unit
    ) {
        val idsToLoad = storeIds.filterNot { skuDetails.containsKey(it) }
        if (idsToLoad.isEmpty()) {
            onReady()
            return
        }

        loadProducts(idsToLoad, onFailed) { details ->
            val skuDetailsMap = details.associateBy { it.sku }
            skuDetails = skuDetails + skuDetailsMap.toMutableMap()

            onReady()
        }
    }

    @Suppress("DEPRECATION")
    override fun getStoreData(storeId: LegacyStoreId): SkuDetails? {
        return skuDetails[storeId]
    }

    @Suppress("DEPRECATION")
    override fun makePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?, // ignored
        applyOffer: Boolean, // ignored
        updatePurchaseInfo: UpdatePurchaseInfo?,
        onFailed: (error: BillingError) -> Unit
    ) {
        val skuDetails = product.skuDetail ?: return

        logger.debug("makePurchase() -> Purchasing the sku: ${skuDetails.sku}")

        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .setSubscriptionUpdateParams(updatePurchaseInfo)
            .build()

        launchBillingFlow(activity, params)
    }

    @Suppress("DEPRECATION")
    override fun queryPurchaseForProduct(
        product: QProduct,
        onCompleted: (BillingResult, Purchase?) -> Unit
    ) {
        val skuDetails = product.skuDetail ?: return

        billingClientHolder.withReadyClient {
            logger.debug(
                "queryPurchaseHistoryForProduct() -> " +
                        "Querying purchase history for ${skuDetails.sku} with type ${skuDetails.type}"
            )

            queryPurchasesAsync(skuDetails.type) { billingResult, purchasesList ->
                onCompleted(
                    billingResult,
                    purchasesList.firstOrNull { skuDetails.sku == it.skus.firstOrNull() }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun queryPurchases(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<Purchase>) -> Unit
    ) {
        billingClientHolder.withReadyClient {
            queryPurchasesAsync(BillingClient.SkuType.SUBS) querySubscriptions@{ subsResult, activeSubs ->
                if (!subsResult.isOk) {
                    handlePurchasesQueryError(subsResult, "subscription", onFailed)
                    return@querySubscriptions
                }

                queryPurchasesAsync(BillingClient.SkuType.INAPP) queryInAppPurchases@{ inAppsResult, unconsumedInApp ->
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
        skuDetails[storeId]?.let {
            onSuccess(QStoreProductType.fromSkuType(it.type))
            return
        }

        loadProducts(listOf(storeId), onFailed) { details ->
            details.firstOrNull()?.takeIf { it.sku == storeId }?.let {
                onSuccess(QStoreProductType.fromSkuType(it.type))
            } ?: onFailed(
                BillingError(
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    "Product not found"
                )
            )
        }
    }

    private fun loadProducts(
        productIds: List<LegacyStoreId>,
        onQuerySkuFailed: (BillingError) -> Unit,
        @Suppress("DEPRECATION") onQuerySkuCompleted: (List<SkuDetails>) -> Unit
    ) {
        querySkuDetailsAsync(
            @Suppress("DEPRECATION")
            BillingClient.SkuType.SUBS,
            productIds,
            { skuDetailsSubs ->
                val skuSubs = skuDetailsSubs.map { it.sku }.toSet()
                val skuInApp = productIds - skuSubs

                if (skuInApp.isNotEmpty()) {
                    querySkuDetailsAsync(
                        @Suppress("DEPRECATION")
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
        @Suppress("DEPRECATION") @BillingClient.SkuType productType: String,
        skuList: List<LegacyStoreId>,
        @Suppress("DEPRECATION") onQuerySkuCompleted: (List<SkuDetails>) -> Unit,
        onQuerySkuFailed: (BillingError) -> Unit
    ) {
        @Suppress("DEPRECATION")
        val params = SkuDetailsParams.newBuilder()
            .setType(productType)
            .setSkusList(skuList)
            .build()

        billingClientHolder.withReadyClient {
            @Suppress("DEPRECATION")
            querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                if (billingResult.isOk && skuDetailsList != null) {
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
    }

    private fun logSkuDetails(
        @Suppress("DEPRECATION") skuDetailsList: List<SkuDetails>,
        skuList: List<LegacyStoreId>
    ) {
        skuDetailsList
            .takeUnless { it.isEmpty() }
            ?.forEach {
                logger.debug("querySkuDetailsAsync() -> $it")
            }
            ?: logger.warn("querySkuDetailsAsync() -> SkuDetails list for $skuList is empty.")
    }
}
