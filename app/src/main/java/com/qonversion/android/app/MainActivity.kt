package com.qonversion.android.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionError
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

                override fun onError(error: QonversionError) {
                    val dsa = error
                }

            })
        }

        billing_flow_subscription.setOnClickListener {
            Qonversion.restore(object : QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    val per = permissions
                    TODO("Not yet implemented")
                }

                override fun onError(error: QonversionError) {
                    val dsa = error
                }

            })
        }
    }
}