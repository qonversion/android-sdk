package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Pair
import androidx.preference.PreferenceManager
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.s.LifecycleCallback
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.billing.Billing
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.logger.StubLogger
import com.qonversion.android.sdk.storage.TokenStorage
import com.qonversion.android.sdk.storage.UserPropertiesStorage
import com.qonversion.android.sdk.validator.TokenValidator

object Qonversion {

    private var billing: QonversionBilling? = null
        private set(value) {
            field = value
            billing?.setReadyListener { purchase, details ->
                purchase(details, purchase)
            }
        }

    private lateinit var userPropertiesManager: QUserPropertiesManager
    private lateinit var attributionManager: QAttributionManager
    private lateinit var productCenterManager: QProductCenterManager

    private lateinit var repository: QonversionRepository
    private lateinit var converter: PurchaseConverter<Pair<SkuDetails, Purchase>>


    @Volatile
    var billingClient: Billing? = billing
        @Synchronized private set
        @Synchronized get

    private const val SDK_VERSION = "1.1.0"
    private const val PROPERTY_UPLOAD_PERIOD = 15 * 1000

    @JvmOverloads
    @JvmStatic
    fun launch(
        context: Application,
        key: String,
        billingBuilder: QonversionBillingBuilder? = null,
        callback: QonversionCallback? = null
    ) {
        if (key.isEmpty()) {
            throw RuntimeException("Qonversion initialization error! Key should not be empty!")
        }

        userPropertiesManager = QUserPropertiesManager()
        attributionManager = QAttributionManager()
        productCenterManager = QProductCenterManager()

        val logger = if (BuildConfig.DEBUG) {
            ConsoleLogger()
        } else {
            StubLogger()
        }
        val storage = TokenStorage(
            PreferenceManager.getDefaultSharedPreferences(context),
            TokenValidator()
        )
        val propertiesStorage = UserPropertiesStorage()
        val environment = EnvironmentProvider(context)
        val config = QonversionConfig(SDK_VERSION, key, true)
        repository = QonversionRepository.initialize(
            context,
            storage,
            propertiesStorage,
            logger,
            environment,
            config,
            "internalUserId"
        )
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

        val fbAttributionId = FacebookAttribution().getAttributionId(context.contentResolver)
            fbAttributionId?.let {
                repository.setProperty(QUserProperties.FacebookAttribution.userPropertyCode,
                    it
                )
            }
      
        val lifecycleCallback = LifecycleCallback(repository)
        context.registerActivityLifecycleCallbacks(lifecycleCallback)
        sendPropertiesAtPeriod(repository)
    }

    private fun sendPropertiesAtPeriod(repository: QonversionRepository) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                repository.sendProperties()
                handler.postDelayed(this, PROPERTY_UPLOAD_PERIOD.toLong())
            }
        }, PROPERTY_UPLOAD_PERIOD.toLong())
    }

    @JvmStatic
    fun purchase(details: SkuDetails, p: Purchase) {
        purchase(android.util.Pair.create(details, p), null)
    }

    @JvmStatic
    fun purchase(details: SkuDetails, p: Purchase, callback: QonversionCallback?) {
        purchase(android.util.Pair.create(details, p), callback)
    }

    private fun purchase(
        purchaseInfo: android.util.Pair<SkuDetails, Purchase>,
        callback: QonversionCallback?
    ) {
        val purchase = converter.convert(purchaseInfo)
        repository.purchase(purchase, callback)
    }

    @JvmStatic
    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource,
        conversionUid: String
    ) {
        repository.attribution(conversionInfo, from.id, conversionUid)
    }

    @JvmStatic
    fun setProperty(key: QUserProperties, value: String) {
        repository.setProperty(key.userPropertyCode, value)
    }

    @JvmStatic
    fun setUserProperty(key: String, value: String) {
        repository.setProperty(key, value)
    }

    @JvmStatic
    fun setUserID(value: String) {
        repository.setProperty(QUserProperties.CustomUserId.userPropertyCode, value)
    }
}


