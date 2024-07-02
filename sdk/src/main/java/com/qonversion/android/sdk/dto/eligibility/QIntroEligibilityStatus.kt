package com.qonversion.android.sdk.dto.eligibility

import com.qonversion.android.sdk.dto.products.QProductType

enum class QIntroEligibilityStatus(val type: String) {
    NonIntroOrTrialProduct("non_intro_or_trial_product"),
    Eligible("intro_or_trial_eligible"),
    Ineligible("intro_or_trial_ineligible"),
    Unknown("unknown");

    companion object {
        fun fromType(type: String): QIntroEligibilityStatus {
            return when (type) {
                "non_intro_or_trial_product" -> NonIntroOrTrialProduct
                "intro_or_trial_eligible" -> Eligible
                "intro_or_trial_ineligible" -> Ineligible
                else -> Unknown
            }
        }

        fun fromProductType(productType: QProductType): QIntroEligibilityStatus {
            return when (productType) {
                QProductType.Intro, QProductType.Trial -> Eligible
                QProductType.Subscription -> Ineligible
                QProductType.InApp -> NonIntroOrTrialProduct
                QProductType.Unknown -> Unknown
            }
        }
    }
}
