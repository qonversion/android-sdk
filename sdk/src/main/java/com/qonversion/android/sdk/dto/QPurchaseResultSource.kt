package com.qonversion.android.sdk.dto

/**
 * Represents the source of a purchase result.
 * - Api: The purchase result was obtained from the Qonversion API.
 * - Local: The purchase result was generated locally by the Qonversion SDK fallback system.
 */
enum class QPurchaseResultSource {
    Api,
    Local
}
