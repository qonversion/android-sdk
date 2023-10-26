package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.ProductDetails

/**
 * This class contains all the information about the concrete Google product,
 * either subscription or in-app. In case of a subscription also determines concrete base plan.
 */
data class QProductStoreDetails(
    /**
     * Original [ProductDetails] received from Google Play Billing Library
     */
    val originalProductDetails: ProductDetails,

    /**
     * Identifier of the base plan which this details relate to.
     * Null for in-app products.
     */
    val basePlanId: String?,
) {
    /**
     * Identifier of a subscription or an in-app product
     */
    val productId: String = originalProductDetails.productId

    /**
     * Name of a subscription or an in-app product
     */
    val name: String = originalProductDetails.name

    /**
     * Title of a subscription or an in-app product
     * The title includes the name of the app.
     */
    val title: String = originalProductDetails.title

    /**
     * Description of a subscription or an in-app product
     */
    val description: String = originalProductDetails.description

    /**
     * Offer details for a subscription.
     * Offer details contain all the available variations of purchase offers,
     * including both base plan and eligible base plan + offer combinations
     * from Google Play Console for current [basePlanId].
     * Null for in-app products.
     */
    val subscriptionOfferDetails: List<QProductOfferDetails>? =
        originalProductDetails.subscriptionOfferDetails
            ?.filter { it.basePlanId == basePlanId }
            ?.map { QProductOfferDetails(it) }

    /**
     * Offer details for an in-app product.
     * Null for subscriptions.
     */
    val inAppOfferDetails: QProductInAppDetails? =
        originalProductDetails.oneTimePurchaseOfferDetails?.let {
            QProductInAppDetails(it)
        }

    val hasTrialOffer: Boolean = subscriptionOfferDetails?.any { it.hasTrial } ?: false

    /**
     *
     */
    val productType: QProductType = when (originalProductDetails.productType) {
        ProductType.SUBS -> if (hasTrialOffer) {
            QProductType.Trial
        } else {
            QProductType.Subscription
        }
        ProductType.INAPP -> QProductType.InApp
        else -> QProductType.InApp
    }
}
