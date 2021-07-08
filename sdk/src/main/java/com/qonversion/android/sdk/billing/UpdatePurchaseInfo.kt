package com.qonversion.android.sdk.billing

import com.android.billingclient.api.BillingFlowParams

data class UpdatePurchaseInfo(
    val purchaseToken: String,
    @BillingFlowParams.ProrationMode val prorationMode: Int? = null
)
