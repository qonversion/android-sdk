package com.qonversion.android.sdk.automations.macros

enum class MacrosType(val type: String) {
    Unknown("Unknown"),
    Price("price"),
    SubscriptionDuration("duration"),
    TrialDuration("trialDuration");

    companion object {
        fun fromType(type: String) = values()
            .find { it.type == type } ?: Unknown
    }
}