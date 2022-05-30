package com.qonversion.android.sdk.entity

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails

data class PurchaseHistory(
    @BillingClient.SkuType val type: String,
    val historyRecord: PurchaseHistoryRecord,
    var skuDetails: SkuDetails? = null
)
