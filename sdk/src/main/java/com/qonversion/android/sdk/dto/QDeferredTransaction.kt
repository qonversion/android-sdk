package com.qonversion.android.sdk.dto

/**
 * Represents a completed deferred purchase transaction with full details.
 *
 * @param productId Store product identifier.
 * @param transactionId Store transaction identifier (purchase token on Android).
 * @param originalTransactionId Original store transaction identifier.
 * @param type Type of the transaction.
 * @param value Transaction value. May be 0.0 if unavailable.
 * @param currency Currency code (e.g. "USD"). May be null if unavailable.
 */
data class QDeferredTransaction(
    val productId: String,
    val transactionId: String?,
    val originalTransactionId: String?,
    val type: QDeferredTransactionType,
    val value: Double,
    val currency: String?
)

/**
 * Represents the type of a deferred transaction.
 */
enum class QDeferredTransactionType {
    Unknown,
    Subscription,
    Consumable,
    NonConsumable
}
