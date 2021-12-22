package com.qonversion.android.sdk.internal.billing.dto

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord

data class PurchaseHistory(
    @BillingClient.SkuType val type: String,
    val historyRecord: PurchaseHistoryRecord
)
