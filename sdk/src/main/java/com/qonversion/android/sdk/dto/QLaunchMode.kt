package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion

/**
 * This enum contains an available settings for Qonversion SDK launch mode.
 * Provide it to the configuration object via [Qonversion.initialize] while initializing the SDK.
 * See more information about launch modes and choose preferred in the documentation
 * https://documentation.qonversion.io/docs/how-qonversion-works
 */
enum class QLaunchMode {
    Analytics, // formerly Observer mode
    SubscriptionManagement // formerly Infrastructure mode
}
