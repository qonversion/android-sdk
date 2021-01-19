package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.BillingClient

enum class QProductType(val type: Int) {
    Trial(0),
    Subscription(1),
    InApp(2);

    companion object {
        fun fromType(type: Int): QProductType {
            return when (type) {
                0 -> Trial
                1 -> Subscription
                2-> InApp
                else -> throw IllegalArgumentException("Undefined enum type")
            }
        }
    }

    fun stringValue(): String {
        return when (this) {
            Subscription -> BillingClient.SkuType.SUBS
            InApp -> BillingClient.SkuType.INAPP
            else -> this.toString()
        }
    }
}
