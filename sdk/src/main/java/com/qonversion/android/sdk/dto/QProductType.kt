package com.qonversion.android.sdk.dto

import com.android.billingclient.api.BillingClient

enum class QProductType(val type: Int) {
    trial(0),
    subscription(1),
    inApp(2);

    companion object {
        fun fromType(type: Int): QProductType {
            return when (type) {
                0 -> trial
                1 -> subscription
                2-> inApp
                else -> throw IllegalArgumentException("Undefined enum type")
            }
        }
    }

    fun stringValue(): String {
        return when (this) {
            subscription -> BillingClient.SkuType.SUBS
            inApp -> BillingClient.SkuType.INAPP
            else -> this.toString()
        }
    }
}
