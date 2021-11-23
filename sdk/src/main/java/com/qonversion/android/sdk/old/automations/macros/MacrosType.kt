package com.qonversion.android.sdk.old.automations.macros

enum class MacrosType(val type: String) {
    Unknown("unknown"),
    Price("price"),
    SubscriptionDuration("duration_subscription"),
    TrialDuration("duration_trial");

    companion object {
        fun fromType(type: String) = values()
            .find { it.type == type } ?: Unknown
    }
}
