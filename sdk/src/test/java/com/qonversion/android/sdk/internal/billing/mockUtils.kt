package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.*
import io.mockk.every
import io.mockk.mockk

@Suppress("DEPRECATION")
fun mockSkuDetails(
    sku: String,
    @BillingClient.SkuType skuType: String
): SkuDetails {

    return mockk<SkuDetails>(relaxed = true).also {
        every { it.sku } returns sku
        every { it.type } returns skuType
    }
}