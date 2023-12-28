package com.qonversion.android.sdk.dto.entitlements

enum class QTransactionType(val type: String) {
    SubscriptionStarted("subscription_started"),
    SubscriptionRenewed("subscription_renewed"),
    TrialStrated("trial_started"),
    IntroStarted("intro_started"),
    IntroRenewed("intro_renewed"),
    NonConsumablePurchase("non_consumable_purchase");

    companion object {
        internal fun fromType(type: String): QTransactionType {
            return when (type) {
                "subscription_started" -> SubscriptionStarted
                "subscription_renewed" -> SubscriptionRenewed
                "trial_started" -> TrialStrated
                "intro_started" -> IntroStarted
                "intro_renewed" -> IntroRenewed
                "non_consumable_purchase" -> NonConsumablePurchase
                else -> SubscriptionRenewed
            }
        }
    }
}