package com.qonversion.android.sdk.dto.entitlements

enum class QEntitlementSource(internal val key: String) {
    Unknown("unknown"), // Unable to detect the source
    AppStore("appstore"), // App Store
    PlayStore("playstore"), // Play Store
    Stripe("stripe"), // Stripe
    Manual("manual"); // The entitlement was activated manually

    companion object {
        fun fromKey(key: String): QEntitlementSource {
            return values().find { it.key == key } ?: Unknown
        }
    }
}
