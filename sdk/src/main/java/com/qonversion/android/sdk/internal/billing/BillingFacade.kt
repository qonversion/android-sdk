package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.old.billing.BillingError
import com.qonversion.android.sdk.old.entity.PurchaseHistory

interface BillingFacade {
    suspend fun queryPurchasesHistory(): List<PurchaseHistory>

    suspend fun queryPurchases(): List<Purchase>

    suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails? = null,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null
    )

    suspend fun loadProducts(productIDs: Set<String>): List<SkuDetails>

    suspend fun consume(purchaseToken: String)

    suspend fun acknowledge(purchaseToken: String)

    suspend fun getSkuDetailsFromPurchases(purchases: List<Purchase>): List<SkuDetails>
}
