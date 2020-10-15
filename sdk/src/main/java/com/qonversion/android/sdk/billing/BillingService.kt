package com.qonversion.android.sdk.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord

internal interface BillingService {
    fun restore()
    fun purchase(activity: Activity, productID: String, @BillingClient.SkuType productType: String)
}

interface BillingServiceDelegate {
    fun handleRestoreCompletedFinished(restoredPurchases: List<PurchaseHistoryRecord>)
    fun handleRestoreCompletedFailed(error: PurchaseError)

    fun handlePurchaseCompletedFinished(purchases: List<Purchase>?)
    fun handlePurchaseCompletedFailed(purchases: List<Purchase>?, error: PurchaseError)
}
