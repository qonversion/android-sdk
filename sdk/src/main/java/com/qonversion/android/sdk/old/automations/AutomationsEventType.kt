package com.qonversion.android.sdk.old.automations

enum class AutomationsEventType(val type: String) {
    Unknown("unknown"),
    TrialStarted("trial_started"),
    TrialConverted("trial_converted"),
    TrialCanceled("trial_canceled"),
    TrialBillingRetry("trial_billing_retry_entered"),
    SubscriptionStarted("subscription_started"),
    SubscriptionRenewed("subscription_renewed"),
    SubscriptionRefunded("subscription_refunded"),
    SubscriptionCanceled("subscription_canceled"),
    SubscriptionBillingRetry("subscription_billing_retry_entered"),
    InAppPurchase("in_app_purchase"),
    SubscriptionUpgraded("subscription_upgraded"),
    TrialStillActive("trial_still_active"),
    TrialExpired("trial_expired"),
    SubscriptionExpired("subscription_expired"),
    SubscriptionDowngraded("subscription_downgraded"),
    SubscriptionProductChanged("subscription_product_changed");

    companion object {
        fun fromType(type: String?) = values().find { it.type == type } ?: Unknown
    }
}
