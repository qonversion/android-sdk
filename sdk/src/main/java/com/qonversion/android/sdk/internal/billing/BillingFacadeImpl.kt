package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.old.billing.BillingError
import com.qonversion.android.sdk.old.entity.PurchaseHistory

class BillingFacadeImpl : BillingFacade {
    override suspend fun queryPurchasesHistory(
        onQueryHistoryCompleted: (purchases: List<PurchaseHistory>) -> Unit,
        onQueryHistoryFailed: (error: BillingError) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun queryPurchases(
        onQueryCompleted: (purchases: List<Purchase>) -> Unit,
        onQueryFailed: (error: BillingError) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails?,
        prorationMode: Int?
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun loadProducts(
        productIDs: Set<String>,
        onLoadCompleted: (products: List<SkuDetails>) -> Unit,
        onLoadFailed: (error: BillingError) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun consume(purchaseToken: String) {
        TODO("Not yet implemented")
    }

    override suspend fun acknowledge(purchaseToken: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getSkuDetailsFromPurchases(
        purchases: List<Purchase>,
        onCompleted: (List<SkuDetails>) -> Unit,
        onFailed: (BillingError) -> Unit
    ) {
        TODO("Not yet implemented")
    }
}
