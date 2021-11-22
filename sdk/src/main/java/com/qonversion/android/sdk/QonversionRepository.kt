package com.qonversion.android.sdk

import android.content.SharedPreferences
import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.Constants.EXPERIMENT_STARTED_EVENT_NAME
import com.qonversion.android.sdk.Constants.PENDING_PUSH_TOKEN_KEY
import com.qonversion.android.sdk.Constants.PUSH_TOKEN_KEY
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.ApiErrorMapper
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.billing.sku
import com.qonversion.android.sdk.billing.stringValue
import com.qonversion.android.sdk.dto.BaseResponse
import com.qonversion.android.sdk.dto.ProviderData
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.dto.automations.Screen
import com.qonversion.android.sdk.dto.eligibility.StoreProductInfo
import com.qonversion.android.sdk.dto.experiments.QExperimentInfo
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.purchase.History
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.dto.purchase.IntroductoryOfferDetails
import com.qonversion.android.sdk.dto.purchase.PurchaseDetails
import com.qonversion.android.sdk.dto.request.*
import com.qonversion.android.sdk.dto.request.data.InitRequestData
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PurchasesCache
import retrofit2.Response

@SuppressWarnings("LongParameterList")
class QonversionRepository internal constructor(
    private val api: Api,
    private val environmentProvider: EnvironmentProvider,
    private val config: QonversionConfig,
    private val logger: Logger,
    private val purchasesCache: PurchasesCache,
    private val errorMapper: ApiErrorMapper,
    private val preferences: SharedPreferences
) {
    private var advertisingId: String? = null
    private var installDate: Long = 0

    private val key = config.key
    private val isDebugMode = config.isDebugMode
    private val sdkVersion = config.sdkVersion
    private val uid get() = config.uid

    // Public functions

    fun init(
        initRequestData: InitRequestData
    ) {
        advertisingId = initRequestData.idfa
        this.installDate = initRequestData.installDate

        val token = preferences.getString(PENDING_PUSH_TOKEN_KEY, null)

        initRequest(initRequestData.purchases, initRequestData.callback, token)
    }

    fun purchase(
        installDate: Long,
        purchase: Purchase,
        experimentInfo: QExperimentInfo?,
        qProductId: String?,
        callback: QonversionLaunchCallback
    ) {
        purchaseRequest(installDate, purchase, experimentInfo, qProductId, callback)
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
        api.attribution(attributionRequest).enqueue {
            onResponse = {
                logger.release("AttributionRequest - ${it.getLogMessage()}")
            }

            onFailure = {
                logger.release("AttributionRequest - failure - ${it?.toQonversionError()}")
            }
        }
    }

    fun sendProperties(
        properties: Map<String, String>,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val propertiesRequest = PropertiesRequest(
            accessToken = key,
            clientUid = uid,
            properties = properties
        )

        api.properties(propertiesRequest).enqueue {
            onResponse = {
                logger.debug("propertiesRequest - ${it.getLogMessage()}")

                if (it.isSuccessful) onSuccess() else onError()
            }
            onFailure = {
                logger.debug("propertiesRequest - failure - ${it?.toQonversionError()}")
                onError()
            }
        }
    }

    fun eligibilityForProductIds(
        productIds: List<String>,
        installDate: Long,
        callback: QonversionEligibilityCallback
    ) {
        val eligibilityRequest = EligibilityRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
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
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.release("eligibilityRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback.onError(it.toQonversionError())
                }
            }
        }
    }

    fun identify(
        userID: String,
        currentUserID: String,
        onSuccess: (identityID: String) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        val identityRequest = IdentityRequest(currentUserID, userID)
        api.identify(identityRequest).enqueue {
            onResponse = {
                logger.release("identityRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data.userID)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.release("identityRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    onError(it.toQonversionError())
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
        api.screens(screenId).enqueue {
            onResponse = {
                logger.release("screensRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
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
        val viewsRequest = ViewsRequest(uid)

        api.views(screenId, viewsRequest).enqueue {
            onResponse = {
                logger.debug("viewsRequest - ${it.getLogMessage()}")
            }
            onFailure = {
                logger.debug("viewsRequest - failure - ${it?.toQonversionError()}")
            }
        }
    }

    fun actionPoints(
        queryParams: Map<String, String>,
        onSuccess: (actionPoint: ActionPointScreen?) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        api.actionPoints(uid, queryParams).enqueue {
            onResponse = {
                logger.release("actionPointsRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data.items.lastOrNull()?.data)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
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

    fun experimentEvents(offering: QOffering) {
        if (offering.experimentInfo == null) {
            return
        }

        val experimentId = offering.experimentInfo.experimentID
        val payload = mapOf(
            "experiment_id" to experimentId
        )

        eventRequest(EXPERIMENT_STARTED_EVENT_NAME, payload)
    }

    private fun eventRequest(eventName: String, payload: Map<String, Any>) {
        val eventRequest = EventRequest(
            userId = uid,
            eventName = eventName,
            payload = payload
        )

        api.events(eventRequest).enqueue {
            onResponse = {
                logger.debug("eventRequest - ${it.getLogMessage()}")
            }
            onFailure = {
                logger.debug("eventRequest - failure - ${it?.toQonversionError()}")
            }
        }
    }

    // Private functions

    private fun createAttributionRequest(
        conversionInfo: Map<String, Any>,
        from: String
    ): AttributionRequest {
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

    private fun purchaseRequest(
        installDate: Long,
        purchase: Purchase,
        experimentInfo: QExperimentInfo?,
        qProductId: String?,
        callback: QonversionLaunchCallback,
        retries: Int = MAX_RETRIES_NUMBER
    ) {
        val purchaseRequest = PurchaseRequest(
            installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            purchase = convertPurchaseDetails(purchase, experimentInfo, qProductId),
            introductoryOffer = convertIntroductoryPurchaseDetail(purchase)
        )

        api.purchase(purchaseRequest).enqueue {
            onResponse = {
                logger.release("purchaseRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && body.success) {
                    callback.onSuccess(body.data)
                } else {
                    handleErrorPurchase(
                        installDate,
                        purchase,
                        experimentInfo,
                        qProductId,
                        callback,
                        errorMapper.getErrorFromResponse(it),
                        retries
                    )
                }
            }
            onFailure = {
                logger.release("purchaseRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    handleErrorPurchase(
                        installDate,
                        purchase,
                        experimentInfo,
                        qProductId,
                        callback,
                        it.toQonversionError(),
                        retries
                    )
                }
            }
        }
    }

    private fun handleErrorPurchase(
        installDate: Long,
        purchase: Purchase,
        experimentInfo: QExperimentInfo?,
        qProductId: String?,
        callback: QonversionLaunchCallback,
        error: QonversionError,
        retries: Int
    ) {
        if (retries > 0) {
            purchaseRequest(installDate, purchase, experimentInfo, qProductId, callback, retries - 1)
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

        if ((purchase.freeTrialPeriod.isNotEmpty() || purchase.introductoryAvailable) &&
            purchase.introductoryPeriodUnit != null &&
            purchase.introductoryPeriodUnitsCount != null) {
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

    private fun convertPurchaseDetails(
        purchase: Purchase,
        experimentInfo: QExperimentInfo? = null,
        qProductId: String? = null
    ): PurchaseDetails {
        val experimentUid = experimentInfo?.experimentID ?: ""
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
            null,
            mapOf("uid" to experimentUid),
            qProductId ?: ""
        )
    }

    private fun convertHistory(historyRecords: List<PurchaseHistoryRecord>): List<History> {
        return historyRecords.mapNotNull {
            val sku = it.sku

            if (sku == null) {
                null
            } else {
                History(
                    sku,
                    it.purchaseToken,
                    it.purchaseTime.milliSecondsToSeconds()
                )
            }
        }
    }

    private fun restoreRequest(
        installDate: Long,
        historyRecords: List<PurchaseHistoryRecord>,
        callback: QonversionLaunchCallback?
    ) {
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
                logger.release("restoreRequest - ${it.getLogMessage()}")

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
            callback?.onError(errorMapper.getErrorFromResponse(response))
        }
    }

    private fun initRequest(
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback? = null,
        pushToken: String? = null
    ) {
        val inapps: List<Inapp> = convertPurchases(purchases)
        val initRequest = InitRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId, pushToken),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            purchases = inapps
        )

        api.init(initRequest).enqueue {
            onResponse = {
                logger.release("initRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && body.success) {
                    if (!pushToken.isNullOrEmpty()) {
                        preferences.edit().remove(PENDING_PUSH_TOKEN_KEY).apply()
                        preferences.edit().putString(PUSH_TOKEN_KEY, pushToken).apply()
                    }

                    callback?.onSuccess(body.data)
                } else {
                    callback?.onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.release("initRequest - failure - ${it?.toQonversionError()}")
                if (it != null) {
                    callback?.onError(it.toQonversionError())
                }
            }
        }
    }

    private fun <T> Response<T>.getLogMessage() =
        if (isSuccessful) "success - $this" else "failure - ${errorMapper.getErrorFromResponse(this)}"

    companion object {
        private const val MAX_RETRIES_NUMBER = 3
    }
}
