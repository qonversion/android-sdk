package com.qonversion.android.sdk.internal.billing.dto

import com.android.billingclient.api.BillingFlowParams

internal data class UpdatePurchaseInfo(
    val purchaseToken: String,
    @BillingFlowParams.ProrationMode val prorationMode: Int? = null
)
