package com.qonversion.android.sdk.dto.products

enum class QProductRenewState(val type: Int) {
    NonRenewable(-1),
    Unknown(0),
    WillRenew(1),
    Canceled(2),
    BillingIssue(3);

    companion object {
        fun fromType(type: Int): QProductRenewState {
            return when (type) {
                -1 -> NonRenewable
                1 -> WillRenew
                2 -> Canceled
                3 -> BillingIssue
                else -> Unknown
            }
        }
    }
}
