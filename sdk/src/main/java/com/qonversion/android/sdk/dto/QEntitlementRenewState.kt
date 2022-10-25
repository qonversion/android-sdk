package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.internal.dto.QProductRenewState

enum class QEntitlementRenewState(val type: String) {
    Unknown("unknown"),
    WillRenew("will_renew"),
    Canceled("canceled"),
    BillingIssue("billing_issue");

    companion object {
        internal fun fromType(type: String): QEntitlementRenewState {
            return when (type) {
                "will_renew" -> WillRenew
                "canceled" -> Canceled
                "billing_issue" -> BillingIssue
                else -> Unknown
            }
        }

        internal fun fromProductRenewState(renewState: QProductRenewState): QEntitlementRenewState {
            return when (renewState) {
                QProductRenewState.WillRenew -> WillRenew
                QProductRenewState.Canceled -> Canceled
                QProductRenewState.BillingIssue -> BillingIssue
                else -> Unknown
            }
        }
    }

    internal fun toProductRenewState() = when (this) {
        WillRenew -> QProductRenewState.WillRenew
        Canceled -> QProductRenewState.Canceled
        BillingIssue -> QProductRenewState.BillingIssue
        else -> QProductRenewState.Unknown
    }
}
