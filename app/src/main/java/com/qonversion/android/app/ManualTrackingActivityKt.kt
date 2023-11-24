package com.qonversion.android.app

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.qonversion.android.sdk.Qonversion

class ManualTrackingActivityKt : AppCompatActivity() {
    private lateinit var client: BillingClient

    private val productDetails: MutableMap<String, ProductDetails> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        client = BillingClient
            .newBuilder(this)
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (!purchases.isNullOrEmpty()) {
                        Qonversion.shared.syncPurchases()
                    }
                }
            }
            .build()

        launchBilling()
    }

    private fun launchBilling() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetailsAsync()
                }
            }

            override fun onBillingServiceDisconnected() {
                // ignore in example
            }
        })
    }

    private fun queryProductDetailsAsync() {
        val product = QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams
            .newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { billingResult, details ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (details.isNotEmpty()) {
                    productDetails[PRODUCT_ID] = details.first()
                }
                launchBillingFlow()
            }
        }
    }

    private fun launchBillingFlow() {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(productDetails[PRODUCT_ID]!!)
            .build()

        val params = BillingFlowParams
            .newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        client.launchBillingFlow(this, params)
    }

    companion object {
        private const val PRODUCT_ID = "your_product_id"
    }
}
