package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails

data class QProductOfferDetails(
    val offerDetails: SubscriptionOfferDetails
) {
    val basePlanId: String = offerDetails.basePlanId

    val offerId: String? = offerDetails.offerId

    val offerToken: String = offerDetails.offerToken

    val tags: List<String> = offerDetails.offerTags

    val pricingPhases: List<QProductPrisingPhase> =
        offerDetails.pricingPhases.pricingPhaseList.map { QProductPrisingPhase(it) }

    val trialPhase: QProductPrisingPhase? = pricingPhases.find {
        it.type === QProductPrisingPhase.PricingPhaseType.FreeTrial
    }

    val hasTrial: Boolean = trialPhase != null
}
