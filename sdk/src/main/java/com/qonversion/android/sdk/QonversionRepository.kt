package com.qonversion.android.sdk

import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.SharedPreferencesKeys.CUSTOM_UID_KEY
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.ApiHeadersProvider
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.billing.stringValue
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.eligibility.StoreProductInfo
import com.qonversion.android.sdk.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.dto.automations.Screen
import com.qonversion.android.sdk.dto.request.ViewsRequest
import com.qonversion.android.sdk.dto.purchase.History
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.dto.purchase.IntroductoryOfferDetails
import com.qonversion.android.sdk.dto.purchase.PurchaseDetails
import com.qonversion.android.sdk.dto.request.*
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PurchasesCache
import com.qonversion.android.sdk.storage.SharedPreferencesCache
import com.qonversion.android.sdk.storage.Storage
import com.qonversion.android.sdk.validator.Validator
import retrofit2.Response

class QonversionRepository internal constructor(
    private val api: Api,
    private var qUidStorage: Storage,
    private val sharedPreferencesCache: SharedPreferencesCache,
    private val environmentProvider: EnvironmentProvider,
    private val sdkVersion: String,
    private val key: String,
    private val isDebugMode: Boolean,
    private val logger: Logger,
    private val requestQueue: RequestsQueue,
    private val requestValidator: Validator<QonversionRequest>,
    private val headersProvider: ApiHeadersProvider,
    private val purchasesCache: PurchasesCache
) {
    private var advertisingId: String? = null
    private var installDate: Long = 0

    // Public functions

    fun init(
        installDate: Long,
        idfa: String? = null,
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback?
    ) {
        advertisingId = idfa
        this.installDate = installDate
        initRequest(purchases, callback)
    }

    fun purchase(
        installDate: Long,
        purchase: Purchase,
        callback: QonversionLaunchCallback
    ) {
        purchaseRequest(installDate, purchase, callback)
    }

    fun restore(
        installDate: Long,
        historyRecords: List<PurchaseHistoryRecord>,
        callback: QonversionLaunchCallback?
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

    fun sendProperties(properties: Map<String, String>, onCompleted: () -> Unit) {
        if (qUidStorage.load().isNotEmpty()) {
            propertiesRequest(properties, onCompleted)
        }
    }

    fun eligibilityForProductIds(
        productIds: List<String>,
        installDate: Long,
        callback: QonversionEligibilityCallback
    ) {
        val uid = qUidStorage.load()
        val customUid = getCustomUid()

        val eligibilityRequest = EligibilityRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            customUid = customUid,
            debugMode = isDebugMode.stringValue(),
            productInfos = productIds.map {
                StoreProductInfo(it)
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

    fun setPushToken(token: String) {
        initRequest(pushToken = token)
    }

    fun screens(
        screenId: String,
        onSuccess: (screen: Screen) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        api.screens(headersProvider.getScreenHeaders(), screenId).enqueue {
            onResponse = {
                logger.release("screensRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data)
                } else {
                    onError(it.toQonversionError())
                }
            }
            onFailure = {
                logger.release("screensRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    onError(it.toQonversionError())
                }
            }
        }
    }

    fun views(screenId: String) {
        val uid = qUidStorage.load()
        val viewsRequest = ViewsRequest(uid)

        api.views(headersProvider.getHeaders(), screenId, viewsRequest).enqueue {
            onResponse = {
                logger.debug("viewsRequest - ${it.getLogMessage()}")
            }
            onFailure = {
                logger.release("viewsRequest - failure - ${it?.toQonversionError()}")
            }
        }
    }

    fun actionPoints(
        queryParams: Map<String, String>,
        onSuccess: (actionPoint: ActionPointScreen?) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        val uid = qUidStorage.load()

        api.actionPoints(headersProvider.getHeaders(), uid, queryParams).enqueue {
            onResponse = {
                logger.release("actionPointsRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data.items.lastOrNull()?.data)
                } else {
                    onError(it.toQonversionError())
                }
            }
            onFailure = {
                logger.release("actionPointsRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    onError(it.toQonversionError())
                }
            }
        }
    }

    // Private functions

    private fun createAttributionRequest(
        conversionInfo: Map<String, Any>,
        from: String
    ): QonversionRequest {
        val uid = qUidStorage.load()
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
        val clientUid = qUidStorage.load()
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
        callback: QonversionLaunchCallback,
        retries: Int = MAX_RETRIES_NUMBER
    ) {
        val uid = qUidStorage.load()
        val customUid = getCustomUid()

        val purchaseRequest = PurchaseRequest(
            installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            customUid = customUid,
            debugMode = isDebugMode.stringValue(),
            purchase = convertPurchaseDetails(purchase),
            introductoryOffer = convertIntroductoryPurchaseDetail(purchase)
        )

        api.purchase(purchaseRequest).enqueue {
            onResponse = {
                logger.release("purchaseRequest - success - $it")
                val body = it.body()
                if (body != null && body.success) {
                    callback.onSuccess(body.data)
                } else {
                    handleErrorPurchase(installDate, purchase, callback, it.toQonversionError(), retries)
                }
                kickRequestQueue()
            }
            onFailure = {
                logger.release("purchaseRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    handleErrorPurchase(installDate, purchase, callback, it.toQonversionError(), retries)
                }
            }
        }
    }

    private fun handleErrorPurchase(
        installDate: Long,
        purchase: Purchase,
        callback: QonversionLaunchCallback,
        error: QonversionError,
        retries: Int
    ) {
        if (retries > 0) {
            purchaseRequest(installDate, purchase, callback, retries - 1)
        } else {
            callback.onError(error)
            purchasesCache.savePurchase(purchase)
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
        callback: QonversionLaunchCallback?
    ) {
        val uid = qUidStorage.load()
        val customUid = getCustomUid()

        val history = convertHistory(historyRecords)
        val request = RestoreRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            customUid = customUid,
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
        callback: QonversionLaunchCallback?
    ) {
        val body = response.body()
        if (body != null && body.success) {
            callback?.onSuccess(body.data)
        } else {
            callback?.onError(response.toQonversionError())
        }
        kickRequestQueue()
    }

    private fun initRequest(
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback? = null,
        pushToken: String? = null
    ) {
        val uid = qUidStorage.load()
        val customUid = getCustomUid()
        val inapps: List<Inapp> = convertPurchases(purchases)
        val initRequest = InitRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId, pushToken),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            customUid = customUid,
            debugMode = isDebugMode.stringValue(),
            purchases = inapps
        )

        api.init(initRequest).enqueue {
            onResponse = {
                logger.release("initRequest - success - $it")
                val body = it.body()
                if (body != null && body.success) {
                    qUidStorage.save(body.data.uid)
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

    private fun propertiesRequest(
        properties: Map<String, String>,
        onCompleted: () -> Unit
    ) {
        val uid = qUidStorage.load()

        val propertiesRequest = PropertiesRequest(
            accessToken = key,
            clientUid = uid,
            properties = properties
        )

        api.properties(propertiesRequest).enqueue {
            onResponse = {
                logger.debug("propertiesRequest - ${it.getLogMessage()}")

                if (it.isSuccessful) {
                    onCompleted()
                }

                kickRequestQueue()
            }
            onFailure = {
                logger.debug("propertiesRequest - failure - ${it?.toQonversionError()}")
            }
        }
    }

    private fun getCustomUid() = sharedPreferencesCache.getString(CUSTOM_UID_KEY, null)

    private fun <T> Response<T>.getLogMessage() = if(isSuccessful) "success - $this" else  "failure - ${this.toQonversionError()}"

    companion object {
        private const val MAX_RETRIES_NUMBER = 3
    }
}
