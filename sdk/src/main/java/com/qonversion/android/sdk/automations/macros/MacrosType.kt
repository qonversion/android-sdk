package com.qonversion.android.sdk.automations.macros

internal enum class MacrosType(val type: String) {
    Unknown("unknown"),
    Price("price"),
    SubscriptionDuration("duration_subscription"),
    TrialDuration("duration_trial");

    companion object {
        fun fromType(type: String) = values()
            .find { it.type == type } ?: Unknown
    }
}
