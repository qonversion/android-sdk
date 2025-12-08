package com.qonversion.android.sdk.dto

/**
 * Represents the status of a purchase operation result.
 *
 * - SUCCESS: The purchase was completed successfully (either through the API or fallback system)
 * - USER_CANCELED: The user canceled the purchase
 * - PENDING: The purchase is pending (awaiting completion)
 * - ERROR: The purchase failed due to an error (and fallback system could not handle it)
 */
enum class QPurchaseResultStatus {
    Success,
    UserCanceled,
    Pending,
    Error
}
