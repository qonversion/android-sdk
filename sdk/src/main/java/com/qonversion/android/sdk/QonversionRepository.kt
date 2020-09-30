package com.qonversion.android.sdk

import android.app.Application
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.extractor.Extractor
import com.qonversion.android.sdk.extractor.TokenExtractor
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import com.qonversion.android.sdk.storage.Storage
import com.qonversion.android.sdk.validator.RequestValidator
import com.qonversion.android.sdk.validator.Validator
import com.squareup.moshi.Moshi
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit


internal class QonversionRepository private constructor(
    private val api: Api,
    private var storage: Storage,
    private var propertiesStorage: PropertiesStorage,
    private val environmentProvider: EnvironmentProvider,
    private val sdkVersion: String,
    private val trackingEnabled: Boolean,
    private val key: String,
    private val logger: Logger,
    private val internalUserId: String,
    private val requestQueue: RequestsQueue,
    private val tokenExtractor: Extractor<retrofit2.Response<BaseResponse<Response>>>,
    private val requestValidator: Validator<QonversionRequest>
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

    fun attribution(conversionInfo: Map<String, Any>, from: String, conversionUid: String) {
        val attributionRequest = createAttributionRequest(conversionInfo, from, conversionUid)
        if (requestValidator.valid(attributionRequest)) {
            logger.log("QonversionRepository: request: [${attributionRequest.javaClass.simpleName}] authorized: [TRUE]")
            sendQonversionRequest(attributionRequest)
        } else {
            logger.log("QonversionRepository: request: [${attributionRequest.javaClass.simpleName}] authorized: [FALSE]")
            requestQueue.add(attributionRequest)
        }
    }

    fun setProperty(key: String, value: String) {
        propertiesStorage.save(key, value)
    }

    fun sendProperties() {
        if (propertiesStorage.getProperties().isNotEmpty()) {
            propertiesRequest()
        }
    }

    private fun createAttributionRequest(conversionInfo: Map<String, Any>, from: String, conversionUid: String): QonversionRequest {
        val uid = storage.load()
        val adsDto = AdsDto(trackingEnabled, edfa = null)
        return AttributionRequest(
            d = environmentProvider.getInfo(
                internalUserId,
                adsDto
            ),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid,
            providerData = ProviderData(
                data = conversionInfo,
                provider = from,
                uid = conversionUid
            )
        )
    }

    private fun sendQonversionRequest(request: QonversionRequest) {
        when (request) {
            is AttributionRequest -> {
                api.attribution(request).enqueue {
                    onResponse = {
                        logger.log("QonversionRequest - success - $it")
                        kickRequestQueue()
                    }

                    onFailure = {
                        logger.log("QonversionRequest - failure - $it")
                    }
                }
            }
        }
    }

    private fun kickRequestQueue() {
        val clientUid = storage.load()
        if (clientUid.isNotEmpty() && !requestQueue.isEmpty()) {
            logger.log("QonversionRepository: kickRequestQueue queue is not empty")
            val request = requestQueue.poll()
            logger.log("QonversionRepository: kickRequestQueue next request ${request?.javaClass?.simpleName}")
            if (request != null) {
                request.authorize(clientUid)
                sendQonversionRequest(request)
            }
        }
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
                logger.log("purchaseRequest - success - $it")
                callback?.onSuccess(storage.load())
                kickRequestQueue()
            }
            onFailure = {
                logger.log("purchaseRequest - failure - $it")
                if (it != null) {
                    callback?.onError(it)
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
                logger.log("initRequest - success - $it")
                val savedUid = saveUid(it)
                callback?.onSuccess(savedUid)
                kickRequestQueue()
            }
            onFailure = {
                if (it != null) {
                    logger.log("initRequest - failure - $it")
                    callback?.onError(it)
                }
            }
        }
    }

    private fun propertiesRequest() {
        val uid = storage.load()
        val adsDto = AdsDto(trackingEnabled, edfa = null)

        val propertiesRequest = PropertiesRequest(
            d = environmentProvider.getInfo(
                internalUserId,
                adsDto
            ),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid,
            properties = propertiesStorage.getProperties()
        )

        api.properties(propertiesRequest).enqueue {
            onResponse = {
                logger.log("propertiesRequest - success - $it")
                propertiesStorage.clear()
                kickRequestQueue()
            }
            onFailure = {
                logger.log("propertiesRequest - failure - $it")
            }
        }
    }

    private fun saveUid(response: retrofit2.Response<BaseResponse<Response>>): String {
        val token = tokenExtractor.extract(response)
        storage.save(token)
        return token
    }

    companion object {

        private const val BASE_URL = "https://api.qonversion.io/"
        private const val TIMEOUT = 30L
        private const val CACHE_SIZE = 10485776L //10 MB

        fun initialize(
            context: Application,
            storage: Storage,
            propertiesStorage: PropertiesStorage,
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

            val requestQueue = RequestsQueue(logger)

            return QonversionRepository(
                retrofit.create(Api::class.java),
                storage,
                propertiesStorage,
                environmentProvider,
                config.key,
                config.trackingEnabled,
                config.sdkVersion,
                logger,
                internalUserId,
                requestQueue,
                TokenExtractor(),
                RequestValidator()
            )
        }
    }
}
