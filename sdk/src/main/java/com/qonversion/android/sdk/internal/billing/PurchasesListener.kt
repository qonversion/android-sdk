package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.internal.billing.dto.BillingError

internal interface PurchasesListener {
    fun onPurchasesCompleted(purchases: List<Purchase>)
    fun onPurchasesFailed(
        purchases: List<Purchase>,
        error: BillingError
    )
}
