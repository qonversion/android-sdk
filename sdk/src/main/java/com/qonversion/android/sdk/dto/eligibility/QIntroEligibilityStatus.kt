package com.qonversion.android.sdk.dto.eligibility

enum class QIntroEligibilityStatus(val type: String) {
    NonIntroProduct("non_intro_or_trial_product"),
    Eligible("intro_or_trial_eligible"),
    Ineligible("intro_or_trial_ineligible"),
    Unknown("unknown");

    companion object {
        fun fromType(type: String): QIntroEligibilityStatus {
            return when (type) {
                "non_intro_or_trial_product" -> NonIntroProduct
                "intro_or_trial_eligible" -> Eligible
                "intro_or_trial_ineligible" -> Ineligible
                else -> Unknown
            }
        }
    }
}
