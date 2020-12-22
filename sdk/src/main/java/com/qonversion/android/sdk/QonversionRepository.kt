package com.qonversion.android.sdk

import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.ApiHeadersProvider
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.billing.stringValue
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.purchase.History
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.dto.purchase.IntroductoryOfferDetails
import com.qonversion.android.sdk.dto.purchase.PurchaseDetails
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import com.qonversion.android.sdk.storage.Storage
import com.qonversion.android.sdk.validator.Validator

class QonversionRepository internal constructor(
    private val api: Api,
    private var storage: Storage,
    private var propertiesStorage: PropertiesStorage,
    private val environmentProvider: EnvironmentProvider,
    private val sdkVersion: String,
    private val key: String,
    private val logger: Logger,
    private val requestQueue: RequestsQueue,
    private val requestValidator: Validator<QonversionRequest>,
    private val isDebugMode: Boolean,
    private val headersProvider: ApiHeadersProvider
) {
    private var advertisingId: String? = null
    private var installDate: Long? = null

    // Public functions

    fun init(
        installDate: Long,
        idfa: String? = null,
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback?
    ) {
        advertisingId = idfa
        this.installDate = installDate
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

    fun setPushToken(token: String) {
        initRequest(installDate = installDate, token = token)
    }

    fun screens(
        screenId: String,
        callback: QonversionScreensCallback
    ) {
        api.screens(headersProvider.getScreenHeaders(), screenId).enqueue {
            onResponse = {
                val logMessage = if (it.isSuccessful) "success - $it" else "failure - ${it.toQonversionError()}"
                logger.release("screensRequest - $logMessage")

                val body = it.body()
                if (body != null && it.isSuccessful) {
                    callback.onSuccess(body.data.htmlPage)
                } else {
                    callback.onError(it.toQonversionError())
                }
            }
            onFailure = {
                logger.release("screensRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback.onError(it.toQonversionError())
                }
            }
        }
    }

    fun views(screenId: String){
        val uid = storage.load()
        val viewsRequest = ViewsRequest(uid)

        api.views(headersProvider.getHeaders(), screenId, viewsRequest).enqueue {
            onResponse = {
                val logMessage = if (it.isSuccessful) "success - $it" else "failure - ${it.toQonversionError()}"
                logger.release("viewsRequest - $logMessage")
            }
            onFailure = {
                logger.release("viewsRequest - failure - ${it?.toQonversionError()}")
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

    private fun convertPurchases(purchases: List<Purchase>?): List<Inapp> {
        val inapps: MutableList<Inapp> = mutableListOf()

        purchases?.forEach {
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
        response: retrofit2.Response<BaseResponse<QLaunchResult>>,
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
        installDate: Long? = null,
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback? = null,
        token: String? = null
    ) {
        val uid = storage.load()
        val inapps: List<Inapp> = convertPurchases(purchases)
        val initRequest = InitRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId, token),
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
                val logMessage =  if(it.isSuccessful) "success - $it" else  "failure - ${it.toQonversionError()}"
                logger.debug("propertiesRequest - $logMessage")

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
}
