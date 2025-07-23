package com.qonversion.android.sdk.internal.dto

import com.android.billingclient.api.BillingClient

internal enum class QStoreProductType {
    InApp,
    Subscription;

    @BillingClient.ProductType
    fun toProductType(): String {
        return when (this) {
            InApp -> BillingClient.ProductType.INAPP
            Subscription -> BillingClient.ProductType.SUBS
        }
    }

    companion object {
        fun fromProductType(@BillingClient.ProductType type: String): QStoreProductType {
            return if (type == BillingClient.ProductType.INAPP) {
                InApp
            } else {
                Subscription
            }
        }
    }
}
