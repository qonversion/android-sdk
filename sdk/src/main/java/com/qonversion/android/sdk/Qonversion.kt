package com.qonversion.android.sdk

import android.app.Application
import android.util.Pair
import androidx.preference.PreferenceManager
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.billing.Billing
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.logger.StubLogger
import com.qonversion.android.sdk.storage.TokenStorage
import com.qonversion.android.sdk.validator.TokenValidator

class Qonversion private constructor(
    private val billing: QonversionBilling?,
    private val repository: QonversionRepository,
    private val converter: PurchaseConverter<Pair<SkuDetails, Purchase>>
) {

    init {
        billing?.setReadyListener { purchase, details ->
            purchase(details, purchase)
        }
    }

    @Volatile
    var billingClient: Billing? = billing
        @Synchronized private set
        @Synchronized get


    companion object {

        private const val SDK_VERSION = "1.0.5"

        @JvmStatic
        @Volatile
        var instance: Qonversion? = null
            @Synchronized private set
            @Synchronized get


        @JvmStatic
        fun initialize(
            context: Application,
            key: String,
            internalUserId: String
        ): Qonversion {
            return initialize(context, key, internalUserId, null, false, null)
        }

        @JvmStatic
        fun initialize(
            context: Application,
            key: String,
            internalUserId: String,
            callback: QonversionCallback?
        ): Qonversion {
            return initialize(context, key, internalUserId, null, false, callback)
        }

        @JvmStatic
        fun initialize(
            context: Application,
            key: String,
            internalUserId: String,
            billingBuilder: QonversionBillingBuilder?,
            autoTracking: Boolean
        ): Qonversion {
            return initialize(context, key, internalUserId, billingBuilder, autoTracking, null)
        }

        @JvmStatic
        fun initialize(
            context: Application,
            key: String,
            internalUserId: String,
            billingBuilder: QonversionBillingBuilder?,
            autoTracking: Boolean,
            callback: QonversionCallback?
        ): Qonversion {
            if (instance != null) {
                return instance!!
            }

            if (key.isEmpty()) {
                throw RuntimeException("Qonversion initialization error! Key should not be empty!")
            }

            if (autoTracking && billingBuilder == null) {
                throw RuntimeException("Qonversion initialization error! billingBuilder must not be null, when auto tracking is TRUE")
            }

            val logger = if (BuildConfig.DEBUG) {
                ConsoleLogger()
            } else {
                StubLogger()
            }
            val storage = TokenStorage(
                PreferenceManager.getDefaultSharedPreferences(context),
                TokenValidator()
            )
            val environment = AndroidEnvironmentProvider(context)
            val config = QonversionConfig(
                sdkVersion = SDK_VERSION,
                key = key,
                trackingEnabled = autoTracking
            )
            val repository = QonversionRepository.initialize(
                context,
                storage,
                logger,
                environment,
                config,
                internalUserId
            )
            val converter = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            val adProvider = AdvertisingProvider()
            adProvider.init(context, object : AdvertisingProvider.Callback {
                override fun onSuccess(advertisingId: String, provider: String) {
                    repository.init(advertisingId, callback)
                }

                override fun onFailure(t: Throwable) {
                    repository.init(callback)
                }
            })
            val billingClient = if (billingBuilder != null) {
                QonversionBilling(context, billingBuilder, logger, autoTracking)
            } else {
                null
            }
            return Qonversion(billingClient, repository, converter).also {
                instance = it
            }
        }
    }

    fun purchase(details: SkuDetails, p: Purchase) {
        val purchase = converter.convert(android.util.Pair.create(details, p))
        repository.purchase(purchase)
    }

    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource,
        conversionUid: String
    ) {
        repository.attribution(conversionInfo, from.id, conversionUid)
    }
}


