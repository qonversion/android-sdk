package com.qonversion.android.sdk.dto.entitlements

enum class QTransactionType(val type: String) {
    Unknown("unknown"),
    SubscriptionStarted("subscription_started"),
    SubscriptionRenewed("subscription_renewed"),
    TrialStarted("trial_started"),
    IntroStarted("intro_started"),
    IntroRenewed("intro_renewed"),
    NonConsumablePurchase("non_consumable_purchase");

    companion object {
        internal fun fromType(type: String): QTransactionType {
            return when (type) {
                "subscription_started" -> SubscriptionStarted
                "subscription_renewed" -> SubscriptionRenewed
                "trial_started" -> TrialStarted
                "intro_started" -> IntroStarted
                "intro_renewed" -> IntroRenewed
                "non_consumable_purchase" -> NonConsumablePurchase
                else -> Unknown
            }
        }
    }
}
