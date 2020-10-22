package com.qonversion.android.sdk.billing

import android.app.Activity
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails

internal interface BillingService {
    fun restore(
        onRestoreCompleted: (purchases: List<PurchaseHistoryRecord>) -> Unit,
        onRestoreFailed: (error: BillingError) -> Unit
    )

    fun purchase(activity: Activity, skuDetails: SkuDetails)

    fun loadProducts(
        products: Set<Product>,
        onLoadCompleted: (products: List<SkuDetails>) -> Unit,
        onLoadFailed: (error: BillingError) -> Unit
    )

    fun consume(
        purchaseToken: String,
        onConsumed: (billingResult: BillingResult, purchaseToken: String) -> Unit
    )

    fun acknowledge(
        purchaseToken: String,
        onAcknowledged: (billingResult: BillingResult, purchaseToken: String) -> Unit
    )
}
