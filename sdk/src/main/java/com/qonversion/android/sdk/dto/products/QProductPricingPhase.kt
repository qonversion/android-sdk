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
     * The billing period for which the given price applies.
     */
    val billingPeriod: QSubscriptionPeriod = QSubscriptionPeriod.from(originalPricingPhase.billingPeriod)

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
    val type: Type = when {
        recurrenceMode != RecurrenceMode.FiniteRecurring -> Type.Regular
        price.isFree -> Type.FreeTrial
        billingCycleCount == 1 -> Type.DiscountedSinglePayment
        billingCycleCount > 1 -> Type.DiscountedRecurringPayment
        else -> Type.Unknown
    }

    /**
     * True, if the current phase is a trial period. False otherwise.
     */
    val isTrial: Boolean = type == Type.FreeTrial

    /**
     * True, if the current phase is an intro period. False otherwise.
     * The intro phase is one of single or recurrent discounted payments.
     */
    val isIntro: Boolean = type == Type.DiscountedSinglePayment || type == Type.DiscountedRecurringPayment

    /**
     * True, if the current phase represents the base plan. False otherwise.
     */
    val isBasePlan: Boolean = type == Type.Regular

    /**
     * Recurrence mode of the pricing phase.
     */
    enum class RecurrenceMode(private val code: Int) {
        /**
         * The billing plan payment recurs for infinite billing periods unless canceled.
         */
        InfiniteRecurring(1),

        /**
         * The billing plan payment recurs for a fixed number of billing periods
         * set in [billingCycleCount].
         */
        FiniteRecurring(2),

        /**
         * The billing plan payment is a one-time charge that does not repeat.
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
    enum class Type {
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
        DiscountedSinglePayment,

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
