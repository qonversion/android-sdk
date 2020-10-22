package com.qonversion.android.sdk.billing

import com.android.billingclient.api.BillingClient

data class Product(
    val productID: String,
    @BillingClient.SkuType val productType: String
)