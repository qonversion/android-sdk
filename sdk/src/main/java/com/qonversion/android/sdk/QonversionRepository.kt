package com.qonversion.android.sdk

import android.app.Application
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.Response
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.entity.Ads
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
    private val ads: Ads,
    private val key: String,
    private val logger: Logger,
    private val internalUserId: String
) {

    fun init(callback: QonversionCallback?) {
        initRequest(ads, key, sdkVersion, callback)
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
        val adsDto = AdsDto(ads.trackingEnabled)
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
                price = purchase.introductoryPrice,
                priceAmountMicros = purchase.priceAmountMicros,
                currencyCode = purchase.currencyCode,
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
                callback?.onSuccess(saveUid(it))
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
        ads: Ads,
        key: String,
        sdkVersion: String,
        callback: QonversionCallback?
    ) {
        val uid = storage.load()
        val adsDto = AdsDto(ads.trackingEnabled)
        val initRequest = InitRequest(
            d = environmentProvider.getInfo(internalUserId, adsDto),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid
        )

        api.init(initRequest).enqueue {
            onResponse = {
                callback?.onSuccess(saveUid(it))
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

    fun attributionData(advertisingId: String, provider: String) {
        val adsDto = AdsDto(ads.trackingEnabled)
        val adRequest = AttributionDataRequest(
            d = environmentProvider.getInfo(
                internalUserId,
                adsDto
            ),
            provider = provider,
            advertisingId = advertisingId
        )

        api.attribution(adRequest).enqueue {
            onResponse = {
                logger.log("AttributionRequest - success - $it")
            }
            onFailure = {
                if (it != null) {
                    logger.log("AttributionRequest - failure - $it")
                }
            }
        }
    }

    companion object {

        private const val BASE_URL = "https://qonversion.io/api/"
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
                config.ads,
                config.sdkVersion,
                logger,
                internalUserId
            )
        }
    }
}
