package com.qonversion.android.sdk.internal.billing

import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy

internal data class UpdatePurchaseInfo(
    val purchaseToken: String,
    val updatePolicy: QPurchaseUpdatePolicy? = null
)
