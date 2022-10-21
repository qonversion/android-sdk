package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient

internal data class BillingError(
    @BillingClient.BillingResponseCode val billingResponseCode: Int,
    val message: String
)
