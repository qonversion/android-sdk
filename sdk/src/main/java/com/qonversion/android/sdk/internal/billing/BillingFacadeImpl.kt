package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.old.entity.PurchaseHistory

class BillingFacadeImpl : BillingFacade {
    override suspend fun queryPurchasesHistory(): List<PurchaseHistory> {
        TODO("Not yet implemented")
    }

    override suspend fun queryPurchases(): List<Purchase> {
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

    override suspend fun loadProducts(productIDs: Set<String>): List<SkuDetails> {
        TODO("Not yet implemented")
    }

    override suspend fun consume(purchaseToken: String) {
        TODO("Not yet implemented")
    }

    override suspend fun acknowledge(purchaseToken: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getSkuDetailsFromPurchases(purchases: List<Purchase>): List<SkuDetails> {
        TODO("Not yet implemented")
    }
}
