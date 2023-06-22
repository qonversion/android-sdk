package com.qonversion.android.sdk.internal.purchase

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.*

internal data class PurchaseHistory(
    @Suppress("DEPRECATION") @BillingClient.SkuType val type: String,
    val historyRecord: PurchaseHistoryRecord,
    @Suppress("DEPRECATION") var skuDetails: SkuDetails? = null
)
