package com.qonversion.android.app

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.qonversion.android.sdk.Qonversion
import java.util.*

class ManualTrackingActivityKt : AppCompatActivity() {
    private var client: BillingClient? = null
    private val skuDetails: MutableMap<String, SkuDetails?> =
        HashMap()

    override fun onCreate(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onCreate(savedInstanceState, persistentState)
        client = BillingClient
            .newBuilder(this)
            .enablePendingPurchases()
            .setListener { billingResult, list ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (list != null && list.isNotEmpty()) {
                        trackPurchase(
                            skuDetails[SKU_ID]!!,
                            list[0]
                        )
                    }
                }
            }
            .build()
        launchBilling()
    }

    private fun trackPurchase(
        details: SkuDetails,
        purchase: Purchase
    ) {
        Qonversion.syncPurchases()
    }

    private fun launchBilling() {
        client!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val params =
                        SkuDetailsParams
                            .newBuilder()
                            .setSkusList(listOf(SKU_ID))
                            .setType(BillingClient.SkuType.INAPP)
                            .build()
                    client!!.querySkuDetailsAsync(
                        params
                    ) { billingResult, list ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (!list!!.isEmpty()) {
                                skuDetails[SKU_ID] = list[0]
                            }
                            launchBillingFlow()
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() { // ignore in example
            }
        })
    }

    private fun launchBillingFlow() {
        val params =
            BillingFlowParams
                .newBuilder()
                .setSkuDetails(skuDetails[SKU_ID]!!)
                .build()
        client!!.launchBillingFlow(this, params)
    }

    companion object {
        private const val SKU_ID = "your_sku_id"
    }
}