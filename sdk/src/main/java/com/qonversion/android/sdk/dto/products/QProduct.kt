package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QProduct(
    @Json(name = "id") val qonversionId: String,
    @Json(name = "store_id") val storeId: String?,
    @Json(name = "base_plan_id") val basePlanId: String?,
) {
    /**
     * The store details of this product containing all the information from Google Play including
     * the offers for purchasing the base plan of this product (specified by [basePlanId])
     * in case of a subscription.
     * Null, if the product was not found. If the [basePlanId] is not specified for a subscription
     * product, this field will be presented but the [QProductStoreDetails.subscriptionOfferDetails]
     * will be empty.
     */
    @Transient
    var storeDetails: QProductStoreDetails? = null
        private set

    @Transient
    var offeringId: String? = null

    /**
     * The subscription base plan duration for this product.
     * Null, if it's not a subscription product or the product was not found in Google Play.
     */
    val subscriptionPeriod: QSubscriptionPeriod? get() =
        storeDetails?.defaultSubscriptionOfferDetails?.let {
            return it.basePlan?.billingPeriod
        }

    /**
     * The subscription trial duration of the default offer for this product.
     * See [QProductStoreDetails.defaultSubscriptionOfferDetails] for the information on how we
     * choose the default offer.
     * Null, if it's not a subscription product or the product was not found in Google Play.
     */
    val trialPeriod: QSubscriptionPeriod? get() =
        storeDetails?.defaultSubscriptionOfferDetails?.let {
            return it.trialPhase?.billingPeriod
        }

    /**
     * The calculated type of this product based on the store information.
     */
    val type: QProductType = storeDetails?.productType ?: QProductType.Unknown

    /**
     * Formatted price for this product, including the currency sign.
     */
    val prettyPrice: String? get() = when {
        type == QProductType.InApp -> storeDetails?.inAppOfferDetails?.price?.formattedPrice
        storeDetails?.basePlanSubscriptionOfferDetails != null ->
            storeDetails?.basePlanSubscriptionOfferDetails?.basePlan?.price?.formattedPrice
        else -> null
    }

    internal fun setStoreProductDetails(productDetails: ProductDetails) {
        storeDetails = QProductStoreDetails(productDetails, basePlanId)
    }
}
