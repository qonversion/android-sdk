package com.qonversion.android.sdk.dto

/**
 * Enum representing the type of purchase completion callback.
 * Used to distinguish between legacy callback methods and new result-based methods.
 */
enum class QPurchaseCompletionType {
    /**
     * Legacy callback type with separate parameters for entitlements, error, and cancellation status
     */
    LEGACY,

    /**
     * New result-based callback type with a single PurchaseResult object
     */
    RESULT
}
