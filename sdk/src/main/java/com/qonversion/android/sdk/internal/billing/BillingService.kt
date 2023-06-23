package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.*
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory

internal interface BillingService {
    fun queryPurchasesHistory(
        onQueryHistoryCompleted: (purchases: List<PurchaseHistory>) -> Unit,
        onQueryHistoryFailed: (error: BillingError) -> Unit
    )

    fun queryPurchases(
        onQueryCompleted: (purchases: List<Purchase>) -> Unit,
        onQueryFailed: (error: BillingError) -> Unit
    )

    @Suppress("DEPRECATION")
    fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails? = null,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null
    )

    @Suppress("DEPRECATION")
    fun loadProducts(
        productIDs: Set<String>,
        onLoadCompleted: (products: List<SkuDetails>) -> Unit,
        onLoadFailed: (error: BillingError) -> Unit
    )

    fun consume(
        purchaseToken: String
    )

    fun acknowledge(
        purchaseToken: String
    )

    @Suppress("DEPRECATION")
    fun getSkuDetailsFromPurchases(
        purchases: List<Purchase>,
        onCompleted: (List<SkuDetails>) -> Unit,
        onFailed: (BillingError) -> Unit
    )
}
