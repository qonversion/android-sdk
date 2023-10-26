package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.PricingPhase

/**
 * This class represents a pricing phase, describing how a user pays at a point in time.
 */
data class QProductPricingPhase(
    /**
     * Original [PricingPhase] received from Google Play Billing Library
     */
    val originalPricingPhase: PricingPhase
) {
    /**
     * Price for the current phase.
     */
    val price = QProductPrice(
        originalPricingPhase.priceAmountMicros,
        originalPricingPhase.priceCurrencyCode,
        originalPricingPhase.formattedPrice
    )

    /**
     * Billing period for which the given price applies.
     */
    val billingPeriod: QProductPeriod = QProductPeriod.from(originalPricingPhase.billingPeriod)

    /**
     * Number of cycles for which the billing period is applied.
     */
    val billingCycleCount: Int = originalPricingPhase.billingCycleCount

    /**
     * Recurrence mode for the pricing phase.
     */
    val recurrenceMode: RecurrenceMode = RecurrenceMode.from(originalPricingPhase.recurrenceMode)

    /**
     * Type of the pricing phase.
     */
    val type: PricingPhaseType = when {
        recurrenceMode != RecurrenceMode.FiniteRecurring -> PricingPhaseType.Regular
        price.isFree -> PricingPhaseType.FreeTrial
        billingCycleCount == 1 -> PricingPhaseType.SinglePayment
        billingCycleCount > 1 -> PricingPhaseType.DiscountedRecurringPayment
        else -> PricingPhaseType.Unknown
    }

    /**
     * Recurrence mode of the pricing phase.
     */
    enum class RecurrenceMode(private val code: Int) {
        /**
         * The billing plan payment recurs for infinite billing periods unless cancelled.
         */
        InfiniteRecurring(1),

        /**
         * The billing plan payment recurs for a fixed number of billing period
         * set in [billingCycleCount].
         */
        FiniteRecurring(2),

        /**
         * The billing plan payment is a one time charge that does not repeat.
         */
        NonRecurring(3),

        /**
         * Unknown recurrence mode.
         */
        Unknown(-1);

        companion object {
            fun from(code: Int) = values().find { it.code == code } ?: Unknown
        }
    }

    /**
     * Type of the pricing phase.
     */
    enum class PricingPhaseType {
        /**
         * Regular subscription without any discounts like trial or intro offers.
         */
        Regular,

        /**
         * A free phase.
         */
        FreeTrial,

        /**
         * A phase with a discounted payment for a single period.
         */
        SinglePayment,

        /**
         * A phase with a discounted payment for several periods, described in [billingCycleCount].
         */
        DiscountedRecurringPayment,

        /**
         * Unknown pricing phase type
         */
        Unknown;
    }
}
