package com.qonversion.android.sdk

import android.app.Application
import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.billing.stringValue
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.purchase.History
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.dto.purchase.IntroductoryOfferDetails
import com.qonversion.android.sdk.dto.purchase.PurchaseDetails
import com.qonversion.android.sdk.dto.request.*
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import com.qonversion.android.sdk.storage.Storage
import com.qonversion.android.sdk.validator.RequestValidator
import com.qonversion.android.sdk.validator.Validator
import com.squareup.moshi.Moshi
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class QonversionRepository private constructor(
    private val api: Api,
    private var storage: Storage,
    private var propertiesStorage: PropertiesStorage,
    private val environmentProvider: EnvironmentProvider,
    private val sdkVersion: String,
    private val key: String,
    private val logger: Logger,
    private val requestQueue: RequestsQueue,
    private val requestValidator: Validator<QonversionRequest>,
    private val isDebugMode: Boolean
) {
    private var advertisingId: String? = null

    // Public functions

    fun init(
        installDate: Long,
        idfa: String? = null,
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback?
    ) {
        advertisingId = idfa
        initRequest(installDate, idfa, purchases, callback)
    }

    fun purchase(
        installDate: Long,
        purchase: Purchase,
        callback: QonversionPermissionsCallback
    ) {
        purchaseRequest(installDate, purchase, callback)
    }

    fun restore(
        installDate: Long,
        historyRecords: List<PurchaseHistoryRecord>,
        callback: QonversionPermissionsCallback?
    ) {
        restoreRequest(installDate, historyRecords, callback)
    }

    fun attribution(conversionInfo: Map<String, Any>, from: String) {
        val attributionRequest = createAttributionRequest(conversionInfo, from)
        if (requestValidator.valid(attributionRequest)) {
            logger.debug("QonversionRepository: request: [${attributionRequest.javaClass.simpleName}] authorized: [TRUE]")
            sendQonversionRequest(attributionRequest)
        } else {
            logger.debug("QonversionRepository: request: [${attributionRequest.javaClass.simpleName}] authorized: [FALSE]")
            requestQueue.add(attributionRequest)
        }
    }

    fun setProperty(key: String, value: String) {
        propertiesStorage.save(key, value)
    }

    fun sendProperties() {
        if (propertiesStorage.getProperties().isNotEmpty() && storage.load().isNotEmpty()) {
            propertiesRequest()
        }
    }

    fun eligibilityForProductIds(
        productIds: List<String>,
        installDate: Long,
        callback: QonversionEligibilityCallback
    ) {
        val uid = storage.load()

        val eligibilityRequest = EligibilityRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            ids = productIds.map {
                StoreId(it)
            }
        )

        api.eligibility(eligibilityRequest).enqueue {
            onResponse = {
                logger.debug("eligibilityRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && body.success) {
                    callback.onSuccess(body.data.productsEligibility)
                } else {
                    callback.onError(it.toQonversionError())
                }
                kickRequestQueue()
            }
            onFailure = {
                logger.release("eligibilityRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback.onError(it.toQonversionError())
                }
            }
        }
    }

    // Private functions

    private fun createAttributionRequest(
        conversionInfo: Map<String, Any>,
        from: String
    ): QonversionRequest {
        val uid = storage.load()
        return AttributionRequest(
            d = environmentProvider.getInfo(),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid,
            providerData = ProviderData(
                data = conversionInfo,
                provider = from
            )
        )
    }

    private fun sendQonversionRequest(request: QonversionRequest) {
        when (request) {
            is AttributionRequest -> {
                api.attribution(request).enqueue {
                    onResponse = {
                        logger.release("QonversionRequest - success - $it")
                        kickRequestQueue()
                    }

                    onFailure = {
                        logger.release("QonversionRequest - failure - $it")
                    }
                }
            }
        }
    }

    private fun kickRequestQueue() {
        val clientUid = storage.load()
        if (clientUid.isNotEmpty() && !requestQueue.isEmpty()) {
            logger.debug("QonversionRepository: kickRequestQueue queue is not empty")
            val request = requestQueue.poll()
            logger.debug("QonversionRepository: kickRequestQueue next request ${request?.javaClass?.simpleName}")
            if (request != null) {
                request.authorize(clientUid)
                sendQonversionRequest(request)
            }
        }
    }

    private fun purchaseRequest(
        installDate: Long,
        purchase: Purchase,
        callback: QonversionPermissionsCallback
    ) {
        val uid = storage.load()
        val purchaseRequest = PurchaseRequest(
            installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            purchase = convertPurchaseDetails(purchase),
            introductoryOffer = convertIntroductoryPurchaseDetail(purchase)
        )

        api.purchase(purchaseRequest).enqueue {
            onResponse = {
                logger.release("purchaseRequest - success - $it")
                handlePermissionsResponse(it, callback)
            }
            onFailure = {
                logger.release("purchaseRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback.onError(it.toQonversionError())
                }
            }
        }
    }

    private fun convertPurchases(purcahses: List<Purchase>?): List<Inapp> {
        val inapps: MutableList<Inapp> = mutableListOf()

        purcahses?.forEach {
            val inapp = convertPurchase(it)
            inapps.add(inapp)
        }

        return inapps.toList()
    }

    private fun convertPurchase(purchase: Purchase): Inapp {
        val purchaseDetails = convertPurchaseDetails(purchase)
        val introductoryOfferDetails = convertIntroductoryPurchaseDetail(purchase)

        return Inapp(purchaseDetails, introductoryOfferDetails)
    }

    private fun convertIntroductoryPurchaseDetail(purchase: Purchase): IntroductoryOfferDetails? {
        var introductoryOfferDetails: IntroductoryOfferDetails? = null

        if ((purchase.freeTrialPeriod.isNotEmpty() || purchase.introductoryAvailable)
            && purchase.introductoryPeriodUnit != null
            && purchase.introductoryPeriodUnitsCount != null) {
            introductoryOfferDetails = IntroductoryOfferDetails(
                purchase.introductoryPrice,
                purchase.introductoryPeriodUnit,
                purchase.introductoryPeriodUnitsCount,
                purchase.introductoryPriceCycles,
                purchase.paymentMode
            )
        }

        return introductoryOfferDetails
    }

    private fun convertPurchaseDetails(purchase: Purchase): PurchaseDetails {
        return PurchaseDetails(
            purchase.productId,
            purchase.purchaseToken,
            purchase.purchaseTime,
            purchase.priceCurrencyCode,
            purchase.price,
            purchase.orderId,
            purchase.originalOrderId,
            purchase.periodUnit,
            purchase.periodUnitsCount,
            null
        )
    }

    private fun convertHistory(historyRecords: List<PurchaseHistoryRecord>): List<History> {
        return historyRecords.map {
            History(
                it.sku,
                it.purchaseToken,
                it.purchaseTime.milliSecondsToSeconds()
            )
        }
    }

    private fun restoreRequest(
        installDate: Long,
        historyRecords: List<PurchaseHistoryRecord>,
        callback: QonversionPermissionsCallback?
    ) {
        val uid = storage.load()
        val history = convertHistory(historyRecords)
        val request = RestoreRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            history = history
        )

        api.restore(request).enqueue {
            onResponse = {
                logger.release("restoreRequest - success - $it")
                handlePermissionsResponse(it, callback)
            }
            onFailure = {
                logger.release("restoreRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback?.onError(it.toQonversionError())
                }
            }
        }
    }

    private fun handlePermissionsResponse(
        response: Response<BaseResponse<QLaunchResult>>,
        callback: QonversionPermissionsCallback?
    ) {
        val body = response.body()
        if (body != null && body.success) {
            callback?.onSuccess(body.data.permissions)
        } else {
            callback?.onError(response.toQonversionError())
        }
        kickRequestQueue()
    }

    private fun initRequest(
        installDate: Long,
        idfa: String?,
        purchases: List<Purchase>?,
        callback: QonversionLaunchCallback?
    ) {
        val uid = storage.load()
        val inapps: List<Inapp> = convertPurchases(purchases)
        val initRequest = InitRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(idfa),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            purchases = inapps
        )

        api.init(initRequest).enqueue {
            onResponse = {
                logger.release("initRequest - success - $it")
                val body = it.body()
                if (body != null && body.success) {
                    storage.save(body.data.uid)
                    callback?.onSuccess(body.data)
                } else {
                    callback?.onError(it.toQonversionError())
                }
                kickRequestQueue()
            }
            onFailure = {
                logger.release("initRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback?.onError(it.toQonversionError())
                }
            }
        }
    }

    private fun propertiesRequest() {
        val uid = storage.load()

        val propertiesRequest = PropertiesRequest(
            accessToken = key,
            clientUid = uid,
            properties = propertiesStorage.getProperties()
        )

        api.properties(propertiesRequest).enqueue {
            onResponse = {
                logger.debug("propertiesRequest - ${it.getLogMessage()}")

                if (it.isSuccessful) {
                    propertiesStorage.clear()
                }

                kickRequestQueue()
            }
            onFailure = {
                logger.debug("propertiesRequest - failure - ${it?.toQonversionError()}")
            }
        }
    }

    private fun <T> Response<T>.getLogMessage() = if(isSuccessful) "success - $this" else  "failure - ${this.toQonversionError()}"

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
            config: QonversionConfig
        ): QonversionRepository {

            val client = OkHttpClient.Builder()
                .cache(Cache(context.cacheDir, CACHE_SIZE))
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build()

            val moshiBuilder = Moshi.Builder()
                .add(QProductDurationAdapter())
                .add(QDateAdapter())
                .add(QProductsAdapter())
                .add(QPermissionsAdapter())
                .add(QProductTypeAdapter())
                .add(QProductRenewStateAdapter())
                .add(QOfferingsAdapter())
                .add(QOfferingTagAdapter())
                .add(QEligibilityStatusAdapter())
                .add(QEligibilityAdapter())
            val moshi = moshiBuilder.build()

            val retrofit = Retrofit.Builder()
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .baseUrl(BASE_URL)
                .client(client)
                .build()

            val requestQueue = RequestsQueue(logger)

            return QonversionRepository(
                retrofit.create(Api::class.java),
                storage,
                propertiesStorage,
                environmentProvider,
                config.sdkVersion,
                config.key,
                logger,
                requestQueue,
                RequestValidator(),
                config.isDebugMode
            )
        }
    }
}
