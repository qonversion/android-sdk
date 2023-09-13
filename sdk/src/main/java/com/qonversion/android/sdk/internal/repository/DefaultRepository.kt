package com.qonversion.android.sdk.internal.repository

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.properties.QUserProperty
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.Constants.PENDING_PUSH_TOKEN_KEY
import com.qonversion.android.sdk.internal.Constants.PRICE_MICROS_DIVIDER
import com.qonversion.android.sdk.internal.Constants.PUSH_TOKEN_KEY
import com.qonversion.android.sdk.internal.EnvironmentProvider
import com.qonversion.android.sdk.internal.IncrementalDelayCalculator
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.api.ApiErrorMapper
import com.qonversion.android.sdk.internal.billing.sku
import com.qonversion.android.sdk.internal.dto.BaseResponse
import com.qonversion.android.sdk.internal.dto.ProviderData
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.SendPropertiesResult
import com.qonversion.android.sdk.internal.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.internal.dto.automations.Screen
import com.qonversion.android.sdk.internal.dto.eligibility.StoreProductInfo
import com.qonversion.android.sdk.internal.dto.purchase.History
import com.qonversion.android.sdk.internal.dto.purchase.Inapp
import com.qonversion.android.sdk.internal.dto.purchase.IntroductoryOfferDetails
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseDetails
import com.qonversion.android.sdk.internal.dto.request.AttachUserRequest
import com.qonversion.android.sdk.internal.dto.request.SendPushTokenRequest
import com.qonversion.android.sdk.internal.dto.request.AttributionRequest
import com.qonversion.android.sdk.internal.dto.request.CrashRequest
import com.qonversion.android.sdk.internal.dto.request.EligibilityRequest
import com.qonversion.android.sdk.internal.dto.request.IdentityRequest
import com.qonversion.android.sdk.internal.dto.request.InitRequest
import com.qonversion.android.sdk.internal.dto.request.PurchaseRequest
import com.qonversion.android.sdk.internal.dto.request.RestoreRequest
import com.qonversion.android.sdk.internal.dto.request.ViewsRequest
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.dto.request.data.UserPropertyRequestData
import com.qonversion.android.sdk.internal.enqueue
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.secondsToMilliSeconds
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.internal.stringValue
import com.qonversion.android.sdk.internal.toQonversionError
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import retrofit2.Response
import java.lang.RuntimeException
import java.util.Timer
import java.util.TimerTask

@SuppressWarnings("LongParameterList")
internal class DefaultRepository internal constructor(
    private val api: Api,
    private val environmentProvider: EnvironmentProvider,
    private val config: InternalConfig,
    private val logger: Logger,
    private val purchasesCache: PurchasesCache,
    private val errorMapper: ApiErrorMapper,
    private val preferences: SharedPreferences,
    private val delayCalculator: IncrementalDelayCalculator
) : QRepository {
    private var advertisingId: String? = null
    private var installDate: Long = 0

    private val key = config.primaryConfig.projectKey
    private val isDebugMode = config.isSandbox
    private val sdkVersion = config.primaryConfig.sdkVersion
    private val uid get() = config.uid

    // Public functions

    override fun init(initRequestData: InitRequestData) {
        advertisingId = initRequestData.idfa
        this.installDate = initRequestData.installDate

        initRequest(initRequestData.purchases, initRequestData.callback)
    }

    override fun remoteConfig(userID: String, callback: QonversionRemoteConfigCallback) {
        val queryParams = mapOf("user_id" to userID)
        api.remoteConfig(queryParams).enqueue {
            onResponse = {
                logger.debug("remoteConfigRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body == null) {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                } else {
                    callback.onSuccess(body)
                }
            }

            onFailure = {
                logger.release("remoteConfigRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        userId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        val request = AttachUserRequest(groupId)
        api.attachUserToExperiment(experimentId, userId, request).enqueue {
            onResponse = {
                logger.debug("attachUserRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    callback.onSuccess()
                } else {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.release("attachUserRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun detachUserFromExperiment(
        experimentId: String,
        userId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        api.detachUserFromExperiment(experimentId, userId).enqueue {
            onResponse = {
                logger.debug("detachUserRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    callback.onSuccess()
                } else {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.release("detachUserRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun purchase(
        installDate: Long,
        purchase: Purchase,
        qProductId: String?,
        callback: QonversionLaunchCallback
    ) {
        purchaseRequest(installDate, purchase, qProductId, callback)
    }

    override fun restore(
        installDate: Long,
        historyRecords: List<PurchaseHistory>,
        callback: QonversionLaunchCallback?
    ) {
        val history = convertHistory(historyRecords)

        restoreRequest(installDate, history, callback)
    }

    override fun attribution(
        conversionInfo: Map<String, Any>,
        from: String,
        onSuccess: (() -> Unit)?,
        onError: ((error: QonversionError) -> Unit)?
    ) {
        val attributionRequest = createAttributionRequest(conversionInfo, from)
        api.attribution(attributionRequest).enqueue {
            onResponse = {
                logger.release("AttributionRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    onSuccess?.invoke()
                } else {
                    onError?.invoke(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.release("AttributionRequest - failure - ${it.toQonversionError()}")
                onError?.invoke(it.toQonversionError())
            }
        }
    }

    override fun sendProperties(
        properties: Map<String, String>,
        onSuccess: (SendPropertiesResult) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        val propertiesRequestData = properties.map { (key, value) -> UserPropertyRequestData(key, value) }

        api.sendProperties(uid, propertiesRequestData).enqueue {
            onResponse = {
                logger.debug("sendPropertiesRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (it.isSuccessful && body != null) {
                    onSuccess(body)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.debug("sendPropertiesRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
            }
        }
    }

    override fun getProperties(
        onSuccess: (List<QUserProperty>) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        api.getProperties(uid).enqueue {
            onResponse = {
                logger.debug("getPropertiesRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (it.isSuccessful && body != null) {
                    onSuccess(body)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.debug("getPropertiesRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
            }
        }
    }

    override fun eligibilityForProductIds(
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
                logger.release("eligibilityRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun identify(
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
                logger.release("identityRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
            }
        }
    }

    override fun sendPushToken(token: String) {
        sendPushTokenRequest(token)
    }

    override fun screens(
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
                logger.release("screensRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
            }
        }
    }

    override fun views(screenId: String) {
        val viewsRequest = ViewsRequest(uid)

        api.views(screenId, viewsRequest).enqueue {
            onResponse = {
                logger.debug("viewsRequest - ${it.getLogMessage()}")
            }
            onFailure = {
                logger.debug("viewsRequest - failure - ${it.toQonversionError()}")
            }
        }
    }

    override fun actionPoints(
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
                logger.release("actionPointsRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
            }
        }
    }

    override fun crashReport(
        crashData: CrashRequest,
        onSuccess: () -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        api.crashLogs(crashData).enqueue {
            onResponse = {
                logger.release("crashReportRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    onSuccess()
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.release("crashReportRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
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
        qProductId: String?,
        callback: QonversionLaunchCallback,
        attemptIndex: Int = 0
    ) {
        val purchaseRequest = PurchaseRequest(
            installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            purchase = convertPurchaseDetails(purchase, qProductId),
            introductoryOffer = convertIntroductoryPurchaseDetail(purchase)
        )

        api.purchase(purchaseRequest).enqueue {
            onResponse = {
                logger.release("purchaseRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && body.success) {
                    callback.onSuccess(body.data)
                } else {
                    handlePurchaseError(
                        purchase,
                        callback,
                        errorMapper.getErrorFromResponse(it),
                        it.code(),
                        attemptIndex
                    ) { nextAttemptIndex ->
                        purchaseRequest(
                            installDate,
                            purchase,
                            qProductId,
                            callback,
                            nextAttemptIndex
                        )
                    }
                }
            }
            onFailure = {
                logger.release("purchaseRequest - failure - ${it.toQonversionError()}")
                handlePurchaseError(
                    purchase,
                    callback,
                    it.toQonversionError(),
                    null,
                    attemptIndex
                ) { nextAttemptIndex ->
                    purchaseRequest(
                        installDate,
                        purchase,
                        qProductId,
                        callback,
                        nextAttemptIndex
                    )
                }
            }
        }
    }

    private fun handlePurchaseError(
        purchase: Purchase,
        callback: QonversionLaunchCallback,
        error: QonversionError,
        errorCode: Int?,
        attemptIndex: Int,
        retry: (attemptIndex: Int) -> Unit
    ) {
        // Retrying only errors caused by client network connection problems (errorCode == null) or server side problems
        if (attemptIndex < MAX_RETRIES_COUNT) {
            val nextAttemptIndex = attemptIndex + 1
            // For the first error retry instantly.
            if (attemptIndex == 0) {
                retry(nextAttemptIndex)
            } else {
                try {
                    // For the rest - add delay (subtracting 1 from delay index, because first one was instant)
                    val delay = delayCalculator.countDelay(0, attemptIndex - 1)
                    Timer("Delayed retry", false).schedule(object : TimerTask() {
                        override fun run() {
                            retry(nextAttemptIndex)
                        }
                    }, delay.toLong().secondsToMilliSeconds())
                } catch (_: RuntimeException) {
                    retry(nextAttemptIndex)
                }
            }
        } else {
            callback.onError(error, errorCode)
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
            purchase.introductoryPeriodUnitsCount != null
        ) {
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
        qProductId: String? = null
    ): PurchaseDetails {
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
            qProductId ?: ""
        )
    }

    private fun convertHistory(historyRecords: List<PurchaseHistory>): List<History> {
        return historyRecords.mapNotNull {
            val sku = it.historyRecord.sku

            if (sku == null) {
                null
            } else {
                History(
                    sku,
                    it.historyRecord.purchaseToken,
                    it.historyRecord.purchaseTime.milliSecondsToSeconds(),
                    it.skuDetails?.priceCurrencyCode,
                    it.skuDetails?.priceAmountMicros?.let { micros -> micros / PRICE_MICROS_DIVIDER }
                        .toString()
                )
            }
        }
    }

    @VisibleForTesting
    internal fun restoreRequest(
        installDate: Long,
        history: List<History>,
        callback: QonversionLaunchCallback?
    ) {
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
                logger.release("restoreRequest - failure - ${it.toQonversionError()}")
                callback?.onError(it.toQonversionError(), null)
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
            callback?.onError(errorMapper.getErrorFromResponse(response), response.code())
        }
    }

    private fun sendPushTokenRequest(token: String) {
        val device = environmentProvider.getInfo()
        val request = SendPushTokenRequest(key, uid, device.deviceId, token)

        api.sendPushToken(request).enqueue {
            onResponse = {
                preferences.edit().remove(PENDING_PUSH_TOKEN_KEY).apply()
                preferences.edit().putString(PUSH_TOKEN_KEY, token).apply()
            }
            onFailure = {
                logger.release("sendPushTokenRequest - failure - ${it.toQonversionError()}")
            }
        }
    }

    private fun initRequest(
        purchases: List<Purchase>? = null,
        callback: QonversionLaunchCallback? = null
    ) {
        val inapps: List<Inapp> = convertPurchases(purchases)
        val initRequest = InitRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
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
                    callback?.onSuccess(body.data)
                } else {
                    callback?.onError(errorMapper.getErrorFromResponse(it), it.code())
                }
            }
            onFailure = {
                logger.release("initRequest - failure - ${it.toQonversionError()}")
                callback?.onError(it.toQonversionError(), null)
            }
        }
    }

    private fun <T> Response<T>.getLogMessage() =
        if (isSuccessful) "success - $this" else "failure - ${errorMapper.getErrorFromResponse(this)}"

    companion object {
        private const val MAX_RETRIES_COUNT = 3
    }
}
