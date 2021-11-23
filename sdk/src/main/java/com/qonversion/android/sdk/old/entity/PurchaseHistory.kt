package com.qonversion.android.sdk.old.entity

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord

data class PurchaseHistory(
    @BillingClient.SkuType val type: String,
    val historyRecord: PurchaseHistoryRecord
)
