package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.ProductDetails
import com.qonversion.android.sdk.internal.billing.pricePerMaxDuration

/**
 * This class contains all the information about the concrete Google product,
 * either subscription or in-app. In case of a subscription also determines concrete base plan.
 */
data class QProductStoreDetails(
    /**
     * Original [ProductDetails] received from Google Play Billing Library.
     */
    val originalProductDetails: ProductDetails,

    /**
     * Identifier of the base plan to which these details relate.
     * Null for in-app products.
     */
    val basePlanId: String?,
) {
    /**
     * Identifier of a subscription or an in-app product.
     */
    val productId: String = originalProductDetails.productId

    /**
     * Name of a subscription or an in-app product.
     */
    val name: String = originalProductDetails.name

    /**
     * Title of a subscription or an in-app product.
     * The title includes the name of the app.
     */
    val title: String = originalProductDetails.title

    /**
     * Description of a subscription or an in-app product.
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
     * The most profitable subscription offer for the client in our opinion from all the available offers.
     * We calculate the cheapest price for the client by comparing all the trial or intro phases
     * along with base plan.
     */
    val defaultSubscriptionOfferDetails: QProductOfferDetails? =
        subscriptionOfferDetails?.minByOrNull { it.pricePerMaxDuration }

    /**
     * Subscription offer details containing only the base plan without any offer.
     */
    val basePlanSubscriptionOfferDetails: QProductOfferDetails? =
        subscriptionOfferDetails?.firstOrNull { it.pricingPhases.size == 1 }

    /**
     * Offer details for an in-app product.
     * Null for subscriptions.
     */
    val inAppOfferDetails: QProductInAppDetails? =
        originalProductDetails.oneTimePurchaseOfferDetails?.let {
            QProductInAppDetails(it)
        }

    /**
     * True if there is any eligible offer with trial
     * for this subscription and base plan combination.
     * False otherwise or for an in-app product.
     */
    val hasTrialOffer: Boolean = subscriptionOfferDetails?.any { it.hasTrial } ?: false

    /**
     * True if there is any eligible offer with intro price
     * for this subscription and base plan combination.
     * False otherwise or for an in-app product.
     */
    val hasIntroOffer: Boolean = subscriptionOfferDetails?.any { it.hasIntro } ?: false

    /**
     * True if there is any eligible offer with trial or intro price
     * for this subscription and base plan combination.
     * False otherwise or for an in-app product.
     */
    val hasTrialOrIntroOffer: Boolean = subscriptionOfferDetails?.any { it.hasTrialOrIntro } ?: false

    /**
     * The type of the current product. The difference from [QProduct.type] is that current field
     * represents the information from Google Play Billing Library and depends on current client
     * trial eligibility (in case of a subscription product), while the [QProduct.type]
     * depends on the value from Qonversion Product Center.
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

    /**
     * True if the product type is InApp.
     */
    val isInApp: Boolean = productType == QProductType.InApp

    /**
     * True if the product type is Subscription.
     */
    val isSubscription: Boolean = productType == QProductType.Trial || productType == QProductType.Subscription

    /**
     * Find an offer with the specified id.
     * @param offerId identifier of the searching offer.
     * @return found offer or null, if an offer with the specified id doesn't exist.
     */
    fun findOffer(offerId: String): QProductOfferDetails? {
        return subscriptionOfferDetails?.find { it.offerId == offerId }
    }
}
