package com.qonversion.android.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionPermissionsCallback
import com.qonversion.android.sdk.billing.Billing
import com.qonversion.android.sdk.dto.QPermission
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private val skuDetailsMap = mutableMapOf<String, SkuDetails?>()
    private val sku_purchase = "qonversion_sample_purchase"
    private val sku_subscription = "qonversion_sample_subscription"
    private var billingClient : Billing? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        billing_flow_purchase.setOnClickListener {
            Qonversion.purchaseProduct("mostly_main", this, callback = object: QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    val dsa = permissions
                }

                override fun onError(t: Throwable) {
                    val dsa = t
                }

            })
//            launchBilling(sku_purchase, BillingClient.SkuType.INAPP)
        }

        billing_flow_subscription.setOnClickListener {
            launchBilling(sku_subscription, BillingClient.SkuType.SUBS)
        }

        initBilling()
    }

    private fun launchBilling(purchaseId: String, type: String) {
        var params = SkuDetailsParams.newBuilder()
            .setType(type)
            .setSkusList(listOf(purchaseId))
            .build()

        billingClient?.querySkuDetailsAsync(params, object: SkuDetailsResponseListener {
            override fun onSkuDetailsResponse(billingResult: BillingResult, skuDetailsList: MutableList<SkuDetails>?) {
                if (billingResult.responseCode == 0) {
                    for (skuDetails in skuDetailsList!!) {
                        monitor.text = skuDetails.originalJson
                        skuDetailsMap[skuDetails.sku] = skuDetails
                    }
                    launchBillingFlow(purchaseId)
                }
            }
        })
    }

    private fun launchBillingFlow(purchaseId: String) {
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetailsMap[purchaseId]!!)
            .build()
        billingClient?.launchBillingFlow(this, billingFlowParams)
    }

    private fun initBilling() {
//        billingClient = Qonversion.billingClient
//
//        billingClient?.startConnection(object : BillingClientStateListener {
//            override fun onBillingServiceDisconnected() {
//                monitor.text = "Billing Connection failed"
//            }
//
//            override fun onBillingSetupFinished(billingResult: BillingResult) {
//                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                    monitor.text = "Billing Connection successful"
//                }
//            }
//        })
    }
}