package com.qonversion.android.sdk

import android.app.Application
import androidx.preference.PreferenceManager
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.entity.Ads
import com.qonversion.android.sdk.storage.TokenStorage

class Qonversion private constructor(
    private val repository: QonversionRepository,
    private val converter: PurchaseConverter<android.util.Pair<SkuDetails, Purchase>>
) {

    companion object {

        private const val SDK_VERSION = "0.1.0"

        @JvmStatic
        var instance: Qonversion? = null
            private set

        @JvmStatic
        fun initialize(
            context: Application,
            key: String,
            adsId: String
        ) : Qonversion {
            return initialize(context, key, adsId, null)
        }

        @JvmStatic
        fun initialize(
            context: Application,
            key: String,
            adsId: String,
            callback: QonversionCallback?
        ) : Qonversion {
            if (instance != null) {
                return instance!!
            }
            val storage = TokenStorage(PreferenceManager.getDefaultSharedPreferences(context))
            val environment = EnvironmentProvider(context)
            val ads = Ads(false, adsId)
            val config = QonversionConfig(SDK_VERSION, key, ads)
            val repository = QonversionRepository.initialize(context, storage, environment, config)
            val converter = GooglePurchaseConverter()
            repository.init(callback)
            return Qonversion(repository, converter).also {
                instance = it
            }
        }
    }

    fun purchase(details: SkuDetails, p: Purchase) {
        purchase(android.util.Pair.create(details, p), null)
    }

    fun purchase(details: SkuDetails, p: Purchase, callback: QonversionCallback?) {
        purchase(android.util.Pair.create(details, p), callback)
    }

    private fun purchase(purchaseInfo: android.util.Pair<SkuDetails, Purchase>, callback: QonversionCallback?) {
        val purchase = converter.convert(purchaseInfo)
        repository.purchase(purchase, callback)
    }
}
