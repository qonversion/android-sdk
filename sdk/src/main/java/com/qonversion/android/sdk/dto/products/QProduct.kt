package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.*
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QPurchaseModel
import com.qonversion.android.sdk.dto.QPurchaseUpdateModel
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QProduct(
    @Json(name = "id") val qonversionID: String,
    @Json(name = "store_id") val storeID: String?,
    @Json(name = "base_plan_id") val basePlanID: String?,
) {
    @Transient
    @Deprecated("Consider using `storeDetails` instead")
    @Suppress("DEPRECATION")
    var skuDetail: SkuDetails? = null

    /**
     * The store details of this product containing all the information from Google Play including
     * the offers for purchasing the base plan of this product (specified by [basePlanID])
     * in case of a subscription.
     * Null, if the product was not found. If the [basePlanID] is not specified for a subscription
     * product, this field will be presented but the [QProductStoreDetails.subscriptionOfferDetails]
     * will be empty.
     */
    @Transient
    var storeDetails: QProductStoreDetails? = null
        private set

    @Transient
    var offeringID: String? = null

    /**
     * The subscription base plan duration for this product. If the [basePlanID] is not specified,
     * the duration is calculated using the deprecated [skuDetail].
     * Null, if it's not a subscription product or the product was not found in Google Play.
     */
    val subscriptionPeriod: QSubscriptionPeriod? get() =
        storeDetails?.defaultSubscriptionOfferDetails?.let {
            return it.basePlan?.billingPeriod
        } ?: @Suppress("DEPRECATION") skuDetail?.subscriptionPeriod
            ?.takeIf { it.isNotBlank() }
            ?.let { QSubscriptionPeriod.from(it) }

    /**
     * The subscription trial duration of the default offer for this product.
     * See [QProductStoreDetails.defaultSubscriptionOfferDetails] for the information on how we
     * choose the default offer.
     * Null, if it's not a subscription product or the product was not found in Google Play.
     */
    val trialPeriod: QSubscriptionPeriod? get() =
        storeDetails?.defaultSubscriptionOfferDetails?.let {
            return it.trialPhase?.billingPeriod
        } ?: @Suppress("DEPRECATION") skuDetail?.freeTrialPeriod
            ?.takeIf { it.isNotBlank() }
            ?.let { QSubscriptionPeriod.from(it) }

    /**
     * The calculated type of this product based on the store information.
     * Using deprecated [skuDetail] for the old subscription products
     * where [basePlanID] is not specified, and [storeDetails] for all the other products.
     */
    val type: QProductType get() {
        val productType = storeDetails?.let {
            if (it.subscriptionOfferDetails?.isNotEmpty() == true || it.inAppOfferDetails != null) {
                it.productType
            } else {
                null
            }
        }

        @Suppress("DEPRECATION")
        return when {
            // We were able to detect the type of the product base on new Google Store details
            productType != null && productType != QProductType.Unknown -> productType
            // The options below use the deprecated Google Store details and are used only for
            // the old subscription products, where basePlanId is not specified.
            skuDetail?.type == BillingClient.SkuType.INAPP -> QProductType.InApp
            trialPeriod != null -> QProductType.Trial
            skuDetail?.introductoryPricePeriod?.isNotBlank() == true -> QProductType.Intro
            subscriptionPeriod != null -> QProductType.Subscription
            else -> QProductType.Unknown
        }
    }

    /**
     * Formatted price of for this product, including the currency sign.
     */
    val prettyPrice: String? get() = when {
        type == QProductType.InApp -> storeDetails?.inAppOfferDetails?.price?.formattedPrice
        storeDetails?.basePlanSubscriptionOfferDetails != null ->
            storeDetails?.basePlanSubscriptionOfferDetails?.basePlan?.price?.formattedPrice
        else -> @Suppress("DEPRECATION") skuDetail?.price
    }

    /**
     * Converts this product to purchase model to pass to [Qonversion.purchase].
     * @param offerId concrete offer identifier if necessary.
     *                If the products' base plan id is specified, but offer id is not provided for
     *                purchase, then default offer will be used.
     *                Ignored if base plan id is not specified.
     * To know how we choose the default offer, see [QProductStoreDetails.defaultSubscriptionOfferDetails].
     * @return purchase model to pass to the purchase method.
     */
    fun toPurchaseModel(offerId: String? = null): QPurchaseModel {
        return QPurchaseModel(qonversionID, offerId)
    }

    /**
     * Converts this product to purchase model to pass to [Qonversion.purchase].
     * @param offer concrete offer which you'd like to purchase.
     * @return purchase model to pass to the purchase method.
     */
    fun toPurchaseModel(offer: QProductOfferDetails?): QPurchaseModel {
        val model = toPurchaseModel(offer?.offerId)
        // Remove offer for the case when provided offer details are for bare base plan.
        if (offer != null && offer.offerId == null) {
            model.removeOffer()
        }

        return model
    }

    /**
     * Converts this product to purchase update (upgrade/downgrade) model
     * to pass to [Qonversion.updatePurchase].
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade
     *                     will be initialized.
     * @param updatePolicy purchase update policy.
     * @return purchase model to pass to the update purchase method.
     */
    fun toPurchaseUpdateModel(
        oldProductId: String,
        updatePolicy: QPurchaseUpdatePolicy? = null
    ): QPurchaseUpdateModel {
        return QPurchaseUpdateModel(qonversionID, oldProductId, updatePolicy)
    }

    internal fun setStoreProductDetails(productDetails: ProductDetails) {
        storeDetails = QProductStoreDetails(productDetails, basePlanID)
    }
}
