package com.qonversion.android.sdk.old.billing

import com.android.billingclient.api.BillingFlowParams

data class UpdatePurchaseInfo(
    val purchaseToken: String,
    @BillingFlowParams.ProrationMode val prorationMode: Int? = null
)
