package com.qonversion.android.sdk.internal.dto

import com.android.billingclient.api.BillingClient

internal enum class QStoreProductType {
    InApp,
    Subscription;

    @BillingClient.ProductType
    fun toProductType(): String {
        return when(this) {
            InApp -> BillingClient.ProductType.INAPP
            Subscription -> BillingClient.ProductType.SUBS
        }
    }

    @Suppress("DEPRECATION")
    @BillingClient.SkuType
    fun toSkuType(): String {
        return when(this) {
            InApp -> BillingClient.SkuType.INAPP
            Subscription -> BillingClient.SkuType.SUBS
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

        @Suppress("DEPRECATION")
        fun fromSkuType(@BillingClient.SkuType type: String): QStoreProductType {
            return if (type == BillingClient.SkuType.INAPP) {
                InApp
            } else {
                Subscription
            }
        }
    }
}