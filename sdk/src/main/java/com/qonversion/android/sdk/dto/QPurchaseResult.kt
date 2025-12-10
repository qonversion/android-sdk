package com.qonversion.android.sdk.dto

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.entitlements.QEntitlement

/**
 * Represents the result of a purchase operation.
 * Contains all relevant information about the purchase outcome including entitlements,
 * errors, purchase details, and user cancellation status.
 *
 * @param status Status of the purchase operation: SUCCESS, USER_CANCELED, PENDING, or ERROR
 * @param entitlements Map of entitlements obtained as a result of the purchase
 * @param purchase Google Play Billing Purchase object associated with the purchase
 * @param error Error that occurred during the purchase, if any
 * @param source Source of this purchase result: Api or Local (fallback system)
 */
class QPurchaseResult private constructor(
    val status: QPurchaseResultStatus,
    val entitlements: Map<String, QEntitlement>,
    val purchase: Purchase?,
    val error: QonversionError?,
    val source: QPurchaseResultSource
) {

    /**
     * Indicates whether the entitlements were generated locally (by Qonversion SDK fallback system)
     */
    val isFallbackGenerated: Boolean
        get() = source == QPurchaseResultSource.Local

    /**
     * Indicates whether the purchase was completed successfully (either through the API or fallback system)
     */
    val isSuccessful: Boolean
        get() = status == QPurchaseResultStatus.Success

    /**
     * Indicates whether the user canceled the purchase
     */
    val isCanceledByUser: Boolean
        get() = status == QPurchaseResultStatus.UserCanceled

    /**
     * Indicates whether the purchase is pending (awaiting completion)
     */
    val isPending: Boolean
        get() = status == QPurchaseResultStatus.Pending

    /**
     * Indicates whether the purchase failed due to an error
     */
    val isError: Boolean
        get() = status == QPurchaseResultStatus.Error

    internal companion object {
        fun success(
            entitlements: Map<String, QEntitlement>,
            purchase: Purchase
        ): QPurchaseResult {
            return QPurchaseResult(
                QPurchaseResultStatus.Success,
                entitlements,
                purchase,
                null,
                QPurchaseResultSource.Api
            )
        }

        fun successFromFallback(
            entitlements: Map<String, QEntitlement>,
            purchase: Purchase
        ): QPurchaseResult {
            return QPurchaseResult(
                QPurchaseResultStatus.Success,
                entitlements,
                purchase,
                null,
                QPurchaseResultSource.Local
            )
        }

        fun userCanceled(): QPurchaseResult {
            return QPurchaseResult(
                QPurchaseResultStatus.UserCanceled,
                emptyMap(),
                null,
                null,
                QPurchaseResultSource.Api
            )
        }

        fun pending(): QPurchaseResult {
            return QPurchaseResult(
                QPurchaseResultStatus.Pending,
                emptyMap(),
                null,
                null,
                QPurchaseResultSource.Api
            )
        }

        fun error(error: QonversionError): QPurchaseResult {
            return QPurchaseResult(
                QPurchaseResultStatus.Error,
                emptyMap(),
                null,
                error,
                QPurchaseResultSource.Api
            )
        }
    }
}
