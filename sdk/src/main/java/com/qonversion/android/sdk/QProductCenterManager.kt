package com.qonversion.android.sdk

import android.app.Application
import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.s.LifecycleCallback
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.billing.Billing
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.Logger

class QProductCenterManager internal constructor(
    private var repository: QonversionRepository,
    private var logger: Logger
) {
    @Volatile
    var billingClient: Billing? = null
        @Synchronized private set
        @Synchronized get

    private var billing: QonversionBilling? = null
        set(value) {
            value?.setReadyListener { purchase, details ->
                purchase(Pair.create(details, purchase), null)
            }
            field = value
        }

    lateinit var converter: PurchaseConverter<Pair<SkuDetails, Purchase>>

    fun launch(
        context: Application,
        billingBuilder: QonversionBillingBuilder?,
        callback: QonversionCallback?
    ) {
        converter = GooglePurchaseConverter(SkuDetailsTokenExtractor())
        val adProvider = AdvertisingProvider()
        adProvider.init(context, object : AdvertisingProvider.Callback {
            override fun onSuccess(advertisingId: String) {
                repository.init(advertisingId, callback)
            }

            override fun onFailure(t: Throwable) {
                repository.init(callback)
            }
        })

        billing = if (billingBuilder != null) {
            QonversionBilling(context, billingBuilder, logger, true)
        } else {
            null
        }
        billingClient = billing
    }

    fun purchase(
        purchaseInfo: android.util.Pair<SkuDetails, Purchase>,
        callback: QonversionCallback?
    ) {
        val purchase = converter.convert(purchaseInfo)
        repository.purchase(purchase, callback)
    }
}