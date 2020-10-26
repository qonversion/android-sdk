package com.qonversion.android.sdk.billing

import android.app.Activity
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails

internal interface BillingService {
    fun queryPurchasesHistory(
        onQueryHistoryCompleted: (purchases: List<PurchaseHistoryRecord>) -> Unit,
        onQueryHistoryFailed: (error: BillingError) -> Unit
    )

    fun queryPurchases(
        onQueryCompleted: (purchases: List<Purchase>) -> Unit,
        onQueryFailed: (error: BillingError) -> Unit
    )

    fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails? = null,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null
    )

    fun loadProducts(
        products: Set<Product>,
        onLoadCompleted: (products: List<SkuDetails>) -> Unit,
        onLoadFailed: (error: BillingError) -> Unit
    )

    fun consume(
        purchaseToken: String,
        onConsumeFailed: (error: BillingError) -> Unit
    )

    fun acknowledge(
        purchaseToken: String,
        onAcknowledgeFailed: (error: BillingError) -> Unit
    )

    fun getSkuDetailsFromPurchases(
        purchases: List<Purchase>,
        onCompleted: (List<SkuDetails>) -> Unit,
        onFailed: (BillingError) -> Unit
    )
}