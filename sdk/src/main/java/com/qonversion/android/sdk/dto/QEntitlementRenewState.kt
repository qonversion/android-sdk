package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.internal.dto.QProductRenewState

enum class QEntitlementRenewState(val type: String) {
    NonRenewable("non_renewable"),
    Unknown("unknown"),
    WillRenew("will_renew"),
    Canceled("canceled"),
    BillingIssue("billing_issue");

    companion object {
        internal fun fromType(type: String): QEntitlementRenewState {
            return when (type) {
                "non_renewable" -> NonRenewable
                "will_renew" -> WillRenew
                "canceled" -> Canceled
                "billing_issue" -> BillingIssue
                else -> Unknown
            }
        }

        internal fun fromProductRenewState(renewState: QProductRenewState): QEntitlementRenewState {
            return when (renewState) {
                QProductRenewState.NonRenewable -> NonRenewable
                QProductRenewState.WillRenew -> WillRenew
                QProductRenewState.Canceled -> Canceled
                QProductRenewState.BillingIssue -> BillingIssue
                else -> Unknown
            }
        }
    }
}
