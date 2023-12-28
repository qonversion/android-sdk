package com.qonversion.android.sdk.dto.entitlements

import com.qonversion.android.sdk.internal.dto.QProductRenewState

enum class QEntitlementGrantType(val type: String) {
    Purchase("purchase"),
    FamilySharing("family_sharing"),
    Manual("manual"),
    OfferCode("offer_code");

    companion object {
        internal fun fromType(type: String): QEntitlementGrantType {
            return when (type) {
                "purchase" -> Purchase
                "family_sharing" -> FamilySharing
                "manual" -> Manual
                "offerCode" -> OfferCode
                else -> Purchase
            }
        }
    }
}