package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails

/**
 * This class contains all the information about the Google subscription offer details.
 * It might be either a plain base plan details or a base plan with the concrete offer details.
 */
data class QProductOfferDetails(
    /**
     * Original [SubscriptionOfferDetails] received from Google Play Billing Library.
     */
    val originalOfferDetails: SubscriptionOfferDetails
) {
    /**
     * The identifier of the current base plan.
     */
    val basePlanId: String = originalOfferDetails.basePlanId

    /**
     * The identifier of the concrete offer, to which these details belong.
     * Null, if these are plain base plan details.
     */
    val offerId: String? = originalOfferDetails.offerId

    /**
     * A token to purchase the current offer.
     */
    val offerToken: String = originalOfferDetails.offerToken

    /**
     * List of tags set for the current offer.
     */
    val tags: List<String> = originalOfferDetails.offerTags

    /**
     * A time-ordered list of pricing phases for the current offer.
     */
    val pricingPhases: List<QProductPricingPhase> =
        originalOfferDetails.pricingPhases.pricingPhaseList.map { QProductPricingPhase(it) }

    /**
     * A base plan phase details.
     */
    val basePlan: QProductPricingPhase? = pricingPhases.find { it.isBasePlan }

    /**
     * Additional details of an installment plan, if exists.
     */
    val installmentPlanDetails: QProductInstallmentPlanDetails? = originalOfferDetails.installmentPlanDetails?.let {
        QProductInstallmentPlanDetails(it)
    };

    /**
     * A trial phase details, if exists.
     */
    val trialPhase: QProductPricingPhase? = pricingPhases.find { it.isTrial }

    /**
     * An intro phase details, if exists.
     * The intro phase is one of single or recurrent discounted payments.
     */
    val introPhase: QProductPricingPhase? = pricingPhases.find { it.isIntro }

    /**
     * True, if there is a trial phase in the current offer. False otherwise.
     */
    val hasTrial: Boolean = trialPhase != null

    /**
     * True, if there is any intro phase in the current offer. False otherwise.
     * The intro phase is one of single or recurrent discounted payments.
     */
    val hasIntro: Boolean = introPhase != null

    /**
     * True, if there is any trial or intro phase in the current offer. False otherwise.
     * The intro phase is one of single or recurrent discounted payments.
     */
    val hasTrialOrIntro: Boolean = hasTrial || hasIntro
}
