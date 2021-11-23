package com.qonversion.android.sdk.old.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import io.mockk.every
import io.mockk.mockk

fun mockSkuDetails(
    sku: String,
    @BillingClient.SkuType skuType: String
): SkuDetails {

    return mockk<SkuDetails>(relaxed = true).also {
        every { it.sku } returns sku
        every { it.type } returns skuType
    }
}