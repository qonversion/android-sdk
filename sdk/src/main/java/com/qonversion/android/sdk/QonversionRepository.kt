package com.qonversion.android.sdk

import android.app.Application
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.Response
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.Storage
import com.squareup.moshi.Moshi
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

internal class QonversionRepository private constructor(
    private val api: Api,
    private var storage: Storage,
    private val environmentProvider: EnvironmentProvider,
    private val sdkVersion: String,
    private val trackingEnabled: Boolean,
    private val key: String,
    private val logger: Logger,
    private val internalUserId: String
) {

    fun init(callback: QonversionCallback?) {
        initRequest(trackingEnabled, key, sdkVersion, callback, null)
    }

    fun init(edfa: String, callback: QonversionCallback?) {
        initRequest(trackingEnabled, key, sdkVersion, callback, edfa)
    }

    fun purchase(
        purchase: Purchase,
        callback: QonversionCallback?
    ) {
        purchaseRequest(purchase, callback)
    }

    private fun purchaseRequest(
        purchase: Purchase,
        callback: QonversionCallback?
    ) {
        val uid = storage.load()
        val adsDto = AdsDto(trackingEnabled, edfa = null)
        val purchaseRequest = PurchaseRequest(
            d = environmentProvider.getInfo(
                internalUserId,
                adsDto
            ),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid,
            inapp = Inapp(
                detailsToken = purchase.detailsToken,
                title = purchase.title,
                description = purchase.description,
                productId = purchase.productId,
                type = purchase.type,
                originalPrice = purchase.originalPrice,
                originalPriceAmountMicros = purchase.originalPriceAmountMicros,
                priceCurrencyCode = purchase.priceCurrencyCode,
                price = purchase.price,
                priceAmountMicros = purchase.priceAmountMicros,
                subscriptionPeriod = purchase.subscriptionPeriod,
                freeTrialPeriod = purchase.freeTrialPeriod,
                introductoryPriceAmountMicros = purchase.introductoryPriceAmountMicros,
                introductoryPricePeriod = purchase.introductoryPricePeriod,
                introductoryPrice = purchase.introductoryPrice,
                introductoryPriceCycles = purchase.introductoryPriceCycles,
                orderId = purchase.orderId,
                packageName = purchase.packageName,
                purchaseTime = purchase.purchaseTime,
                purchaseState = purchase.purchaseState,
                purchaseToken = purchase.purchaseToken,
                acknowledged = purchase.acknowledged,
                autoRenewing = purchase.autoRenewing
            )
        )

        api.purchase(purchaseRequest).enqueue {
            onResponse = {
                val savedUid = saveUid(it)
                callback?.onSuccess(savedUid)
                logger.log("purchaseRequest - success - $it")
            }
            onFailure = {
                if (it != null) {
                    callback?.onError(it)
                    logger.log("purchaseRequest - failure - $it")
                }
            }
        }
    }

    private fun initRequest(
        trackingEnabled: Boolean,
        key: String,
        sdkVersion: String,
        callback: QonversionCallback?,
        edfa: String?
    ) {
        val uid = storage.load()
        val adsDto = AdsDto(trackingEnabled, edfa)
        val initRequest = InitRequest(
            d = environmentProvider.getInfo(internalUserId, adsDto),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid
        )

        api.init(initRequest).enqueue {
            onResponse = {
                val savedUid = saveUid(it)
                callback?.onSuccess(savedUid)
                logger.log("initRequest - success - $it")
            }
            onFailure = {
                if (it != null) {
                    callback?.onError(it)
                    logger.log("initRequest - failure - $it")
                }
            }
        }
    }

    private fun saveUid(response: retrofit2.Response<BaseResponse<Response>>): String {
        val clientUid = response.body()?.let {
            it.data.clientUid ?: ""
        } ?: ""
        storage.save(clientUid)
        return clientUid
    }

    companion object {

        private const val BASE_URL = "https://api.qonversion.io/"
        private const val TIMEOUT = 30L
        private const val CACHE_SIZE = 10485776L //10 MB

        fun initialize(
            context: Application,
            storage: Storage,
            logger: Logger,
            environmentProvider: EnvironmentProvider,
            config: QonversionConfig,
            internalUserId: String
        ): QonversionRepository {

            val client = OkHttpClient.Builder()
                .cache(Cache(context.cacheDir, CACHE_SIZE))
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build()


            val retrofit = Retrofit.Builder()
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
                .baseUrl(BASE_URL)
                .client(client)
                .build()
            return QonversionRepository(
                retrofit.create(Api::class.java),
                storage,
                environmentProvider,
                config.key,
                config.trackingEnabled,
                config.sdkVersion,
                logger,
                internalUserId
            )
        }
    }
}
