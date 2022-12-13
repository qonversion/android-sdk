package com.qonversion.android.sdk.internal.purchase

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails

internal data class PurchaseHistory(
    @BillingClient.SkuType val type: String,
    val historyRecord: PurchaseHistoryRecord,
    var skuDetails: SkuDetails? = null
)
