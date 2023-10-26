package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.PricingPhase

data class QProductPrisingPhase(
    val pricingPhase: PricingPhase
) {
    val price = QProductPrice(
        pricingPhase.priceAmountMicros,
        pricingPhase.priceCurrencyCode,
        pricingPhase.formattedPrice
    )

    val billingPeriod: QProductPeriod = QProductPeriod.from(pricingPhase.billingPeriod)

    val billingCycleCount: Int = pricingPhase.billingCycleCount

    val recurrenceMode: RecurrenceMode = RecurrenceMode.from(pricingPhase.recurrenceMode)

    val type: PricingPhaseType = when {
        recurrenceMode != RecurrenceMode.FiniteRecurring -> PricingPhaseType.Regular
        price.isFree -> PricingPhaseType.FreeTrial
        billingCycleCount == 1 -> PricingPhaseType.SinglePayment
        billingCycleCount > 1 -> PricingPhaseType.DiscountedRecurringPayment
        else -> PricingPhaseType.Unknown
    }

    enum class RecurrenceMode(private val code: Int) {
        InfiniteRecurring(1),
        FiniteRecurring(2),
        NonRecurring(3),
        Unknown(-1);

        companion object {
            fun from(code: Int) = values().find { it.code == code } ?: Unknown
        }
    }

    enum class PricingPhaseType {
        Regular,
        FreeTrial,
        SinglePayment,
        DiscountedRecurringPayment,
        Unknown;
    }
}
