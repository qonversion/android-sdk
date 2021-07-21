package com.qonversion.android.sdk.automations

enum class AutomationsEvent(val type: String) {
    Unknown("unknown"),
    TrialStarted("trial_started"),
    TrialConverted("trial_converted"),
    TrialCanceled("trial_canceled"),
    TrialBillingRetry("trial_billing_retry"),
    SubscriptionStarted("subscription_started"),
    SubscriptionRenewed("subscription_renewed"),
    SubscriptionRefunded("subscription_refunded"),
    SubscriptionCanceled("subscription_canceled"),
    SubscriptionBillingRetry("subscription_billing_retry"),
    InAppPurchase("inapp_purchase"),
    SubscriptionUpgraded("subscription_upgraded"),
    TrialStillActive("trial_still_active");

    companion object {
        fun fromType(type: String?) = values().find { it.type == type } ?: Unknown
    }
}
