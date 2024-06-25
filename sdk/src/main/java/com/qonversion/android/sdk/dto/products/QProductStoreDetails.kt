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
     * Identifier of the subscription or the in-app product.
     */
    val productId: String = originalProductDetails.productId

    /**
     * Name of the subscription or the in-app product.
     */
    val name: String = originalProductDetails.name

    /**
     * Title of the subscription or the in-app product.
     * The title includes the name of the app.
     */
    val title: String = originalProductDetails.title

    /**
     * Description of the subscription or the in-app product.
     */
    val description: String = originalProductDetails.description

    /**
     * Offer details for the subscription.
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
     * and the base plan.
     */
    val defaultSubscriptionOfferDetails: QProductOfferDetails? =
        subscriptionOfferDetails?.minByOrNull { it.pricePerMaxDuration }

    /**
     * Subscription offer details containing only the base plan without any offer.
     */
    val basePlanSubscriptionOfferDetails: QProductOfferDetails? =
        subscriptionOfferDetails?.firstOrNull { it.pricingPhases.size == 1 }

    /**
     * Offer details for the in-app product.
     * Null for subscriptions.
     */
    val inAppOfferDetails: QProductInAppDetails? =
        originalProductDetails.oneTimePurchaseOfferDetails?.let {
            QProductInAppDetails(it)
        }

    /**
     * True, if there is any eligible offer with a trial
     * for this subscription and base plan combination.
     * False otherwise or for an in-app product.
     */
    val hasTrialOffer: Boolean = subscriptionOfferDetails?.any { it.hasTrial } ?: false

    /**
     * True, if there is any eligible offer with an intro price
     * for this subscription and base plan combination.
     * False otherwise or for an in-app product.
     */
    val hasIntroOffer: Boolean = subscriptionOfferDetails?.any { it.hasIntro } ?: false

    /**
     * True, if there is any eligible offer with a trial or an intro price
     * for this subscription and base plan combination.
     * False otherwise or for an in-app product.
     */
    val hasTrialOrIntroOffer: Boolean = subscriptionOfferDetails?.any { it.hasTrialOrIntro } ?: false

    /**
     * The calculated type of the current product.
     */
    val productType: QProductType = when (originalProductDetails.productType) {
        ProductType.SUBS -> defaultSubscriptionOfferDetails?.let {
            when {
                it.hasTrial -> QProductType.Trial
                it.hasIntro -> QProductType.Intro
                else -> QProductType.Subscription
            }
        } ?: QProductType.Unknown
        ProductType.INAPP -> QProductType.InApp
        else -> QProductType.Unknown
    }

    /**
     * True, if the product type is InApp.
     */
    val isInApp: Boolean = productType == QProductType.InApp

    /**
     * True, if the product type is Subscription.
     */
    val isSubscription: Boolean = productType == QProductType.Trial || productType == QProductType.Subscription

    /**
     * True, if the subscription product is prepaid, which means that users pay in advance -
     * they will need to make a new payment to extend their plan.
     */
    val isPrepaid: Boolean = isSubscription &&
            basePlanSubscriptionOfferDetails?.basePlan?.recurrenceMode == QProductPricingPhase.RecurrenceMode.NonRecurring

    /**
     * True, if the subscription product is installment, which means that users commit
     * to pay for a specified amount of periods every month.
     */
    val isInstallment: Boolean = isSubscription &&
            basePlanSubscriptionOfferDetails?.installmentPlanDetails != null

    /**
     * Find an offer with the specified id.
     * @param offerId identifier of the searching offer.
     * @return found offer or null, if an offer with the specified id doesn't exist.
     */
    fun findOffer(offerId: String): QProductOfferDetails? {
        return subscriptionOfferDetails?.find { it.offerId == offerId }
    }
}
