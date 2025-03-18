package com.qonversion.android.sdk.internal.repository

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.properties.QUserProperty
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.EnvironmentProvider
import com.qonversion.android.sdk.internal.IncrementalDelayCalculator
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.api.ApiErrorMapper
import com.qonversion.android.sdk.internal.api.RequestTrigger
import com.qonversion.android.sdk.internal.billing.productId
import com.qonversion.android.sdk.internal.dto.BaseResponse
import com.qonversion.android.sdk.internal.dto.ProviderData
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.SendPropertiesResult
import com.qonversion.android.sdk.internal.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.internal.dto.automations.Screen
import com.qonversion.android.sdk.internal.dto.eligibility.StoreProductInfo
import com.qonversion.android.sdk.internal.dto.purchase.History
import com.qonversion.android.sdk.internal.dto.purchase.Inapp
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseDetails
import com.qonversion.android.sdk.internal.dto.request.AttachUserRequest
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
import com.qonversion.android.sdk.internal.isInternalServerError
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.secondsToMilliSeconds
import com.qonversion.android.sdk.internal.stringValue
import com.qonversion.android.sdk.internal.toQonversionError
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback

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
    private val errorMapper: ApiErrorMapper,
    private val delayCalculator: IncrementalDelayCalculator
) : QRepository {
    private var advertisingId: String? = null
    private var installDate: Long = 0

    private val key = config.primaryConfig.projectKey
    private val isDebugMode = config.isSandbox
    private val sdkVersion = config.primaryConfig.sdkVersion
    private val uid get() = config.uid

    // Public functions

    override fun init(requestData: InitRequestData) {
        advertisingId = requestData.idfa
        this.installDate = requestData.installDate

        val inapps: List<Inapp> = convertPurchases(requestData.purchases)
        val initRequest = InitRequest(
            installDate = installDate,
            device = environmentProvider.getInfo(advertisingId),
            version = sdkVersion,
            accessToken = key,
            clientUid = uid,
            debugMode = isDebugMode.stringValue(),
            purchases = inapps
        )

        api.init(initRequest, requestData.requestTrigger.key).enqueue {
            onResponse = {
                logger.debug("initRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && body.success) {
                    requestData.callback?.onSuccess(body.data)
                } else {
                    requestData.callback?.onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.error("initRequest - failure - ${it.toQonversionError()}")
                requestData.callback?.onError(it.toQonversionError())
            }
        }
    }

    override fun remoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback) {
        api.remoteConfig(uid, contextKey).enqueue {
            onResponse = {
                logger.debug("remoteConfigRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body == null) {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                } else {
                    if (body.isCorrect) {
                        callback.onSuccess(body)
                    } else {
                        callback.onError(QonversionError(QonversionErrorCode.RemoteConfigurationNotAvailable))
                    }
                }
            }

            onFailure = {
                logger.error("remoteConfigRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun remoteConfigList(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ) {
        api.remoteConfigList(uid, contextKeys, includeEmptyContextKey).enqueue {
            onResponse = {
                logger.debug("remoteConfigListRequest for specific context keys - ${it.getLogMessage()}")
                val body = it.body()
                if (body == null) {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                } else {
                    val res = QRemoteConfigList(body.filter { config -> config.isCorrect })
                    callback.onSuccess(res)
                }
            }

            onFailure = {
                logger.error("remoteConfigRequest for specific context keys - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun remoteConfigList(callback: QonversionRemoteConfigListCallback) {
        api.remoteConfigList(uid).enqueue {
            onResponse = {
                logger.debug("remoteConfigListRequest for all context keys - ${it.getLogMessage()}")
                val body = it.body()
                if (body == null) {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                } else {
                    val res = QRemoteConfigList(body.filter { config -> config.isCorrect })
                    callback.onSuccess(res)
                }
            }

            onFailure = {
                logger.error("remoteConfigRequest for all context keys - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        val request = AttachUserRequest(groupId)
        api.attachUserToExperiment(experimentId, uid, request).enqueue {
            onResponse = {
                logger.debug("attachUserToExperimentRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    callback.onSuccess()
                } else {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.error("attachUserToExperimentRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun detachUserFromExperiment(
        experimentId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        api.detachUserFromExperiment(experimentId, uid).enqueue {
            onResponse = {
                logger.debug("detachUserFromExperimentRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    callback.onSuccess()
                } else {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.error("detachUserFromExperimentRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        api.attachUserToRemoteConfiguration(remoteConfigurationId, uid).enqueue {
            onResponse = {
                logger.debug("attachUserToRemoteConfigurationRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    callback.onSuccess()
                } else {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.error("attachUserToRemoteConfigurationRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
            }
        }
    }

    override fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        api.detachUserFromRemoteConfiguration(remoteConfigurationId, uid).enqueue {
            onResponse = {
                logger.debug("detachUserFromRemoteConfigurationRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    callback.onSuccess()
                } else {
                    callback.onError(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.error("detachUserFromRemoteConfigurationRequest - failure - ${it.toQonversionError()}")
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
        callback: QonversionLaunchCallback,
        requestTrigger: RequestTrigger,
    ) {
        val history = convertHistory(historyRecords)

        restoreRequest(installDate, history, callback, requestTrigger)
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
                logger.debug("AttributionRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    onSuccess?.invoke()
                } else {
                    onError?.invoke(errorMapper.getErrorFromResponse(it))
                }
            }

            onFailure = {
                logger.error("AttributionRequest - failure - ${it.toQonversionError()}")
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
                logger.error("sendPropertiesRequest - failure - ${it.toQonversionError()}")
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
                logger.error("getPropertiesRequest - failure - ${it.toQonversionError()}")
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
                logger.error("eligibilityRequest - failure - ${it.toQonversionError()}")
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
                logger.debug("identityRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data.userID)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.error("identityRequest - failure - ${it.toQonversionError()}")
                onError(it.toQonversionError())
            }
        }
    }

    override fun screens(
        screenId: String,
        onSuccess: (screen: Screen) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        api.screens(screenId).enqueue {
            onResponse = {
                logger.debug("screensRequest - ${it.getLogMessage()}")

                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.error("screensRequest - failure - ${it.toQonversionError()}")
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
                logger.error("viewsRequest - failure - ${it.toQonversionError()}")
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
                logger.debug("actionPointsRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && it.isSuccessful) {
                    onSuccess(body.data.items.lastOrNull()?.data)
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.error("actionPointsRequest - failure - ${it.toQonversionError()}")
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
                logger.debug("crashReportRequest - ${it.getLogMessage()}")
                if (it.isSuccessful) {
                    onSuccess()
                } else {
                    onError(errorMapper.getErrorFromResponse(it))
                }
            }
            onFailure = {
                logger.warn("crashReportRequest - failure - ${it.toQonversionError()}")
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
        )

        api.purchase(purchaseRequest, RequestTrigger.Purchase.key, attemptIndex + 1).enqueue {
            onResponse = {
                logger.debug("purchaseRequest - ${it.getLogMessage()}")
                val body = it.body()
                if (body != null && body.success) {
                    callback.onSuccess(body.data)
                } else {
                    handlePurchaseError(
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
                logger.error("purchaseRequest - failure - ${it.toQonversionError()}")
                handlePurchaseError(
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
        callback: QonversionLaunchCallback,
        error: QonversionError,
        errorCode: Int?,
        attemptIndex: Int,
        retry: (attemptIndex: Int) -> Unit
    ) {
        // Retrying only errors caused by client network connection problems (errorCode == null) or server side problems
        if (attemptIndex < MAX_RETRIES_COUNT && (errorCode == null || errorCode.isInternalServerError())) {
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
            callback.onError(error)
        }
    }

    private fun convertPurchases(purchases: List<Purchase>?): List<Inapp> {
        val inapps: MutableList<Inapp> = mutableListOf()

        purchases?.forEach {
            val inapp = convertPurchaseDetails(it)
            inapps.add(Inapp(inapp))
        }

        return inapps.toList()
    }

    private fun convertPurchaseDetails(
        purchase: Purchase,
        qProductId: String? = null
    ): PurchaseDetails {
        return PurchaseDetails(
            purchase.purchaseToken,
            purchase.purchaseTime,
            purchase.orderId,
            purchase.originalOrderId,
            purchase.storeProductId ?: "",
            qProductId ?: "",
            purchase.contextKeys
        )
    }

    private fun convertHistory(historyRecords: List<PurchaseHistory>): List<History> {
        return historyRecords.mapNotNull {
            val productId = it.historyRecord.productId

            if (productId == null) {
                null
            } else {
                History(
                    productId,
                    it.historyRecord.purchaseToken,
                    it.historyRecord.purchaseTime.milliSecondsToSeconds()
                )
            }
        }
    }

    @VisibleForTesting
    internal fun restoreRequest(
        installDate: Long,
        history: List<History>,
        callback: QonversionLaunchCallback,
        trigger: RequestTrigger,
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

        api.restore(request, trigger.key).enqueue {
            onResponse = {
                logger.debug("restoreRequest - ${it.getLogMessage()}")

                handlePermissionsResponse(it, callback)
            }
            onFailure = {
                logger.error("restoreRequest - failure - ${it.toQonversionError()}")
                callback.onError(it.toQonversionError())
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

    private fun <T> Response<T>.getLogMessage() =
        if (isSuccessful) "success - $this" else "failure - ${errorMapper.getErrorFromResponse(this)}"

    companion object {
        private const val MAX_RETRIES_COUNT = 3
    }
}
