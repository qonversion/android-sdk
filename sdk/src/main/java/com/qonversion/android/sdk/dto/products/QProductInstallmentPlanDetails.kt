package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.InstallmentPlanDetails

/**
 * This class represents the details about the installment plan for a subscription product.
 */
data class QProductInstallmentPlanDetails(
    /**
     * Original [InstallmentPlanDetails] received from Google Play Billing Library
     */
    val originalInstallmentPlanDetails: InstallmentPlanDetails
) {
    /**
     * Committed payments count after a user signs up for this subscription plan.
     */
    val commitmentPaymentsCount: Int =
        originalInstallmentPlanDetails.installmentPlanCommitmentPaymentsCount

    /**
     * Subsequent committed payments count after this subscription plan renews.
     *
     * Returns 0 if the installment plan doesn't have any subsequent commitment,
     * which means this subscription plan will fall back to a normal
     * non-installment monthly plan when the plan renews.
     */
    val subsequentCommitmentPaymentsCount: Int =
        originalInstallmentPlanDetails.subsequentInstallmentPlanCommitmentPaymentsCount
}
