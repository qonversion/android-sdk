package io.qonversion.android.sdk.internal.repository

import io.qonversion.android.sdk.dto.QonversionError
import io.qonversion.android.sdk.dto.QonversionErrorCode
import io.qonversion.android.sdk.dto.properties.QUserProperty
import io.qonversion.android.sdk.internal.api.RequestType
import io.qonversion.android.sdk.internal.api.RateLimiter
import io.qonversion.android.sdk.internal.dto.SendPropertiesResult
import io.qonversion.android.sdk.internal.dto.automations.ActionPointScreen
import io.qonversion.android.sdk.internal.dto.automations.Screen
import io.qonversion.android.sdk.internal.dto.request.CrashRequest
import io.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import io.qonversion.android.sdk.internal.purchase.Purchase
import io.qonversion.android.sdk.internal.purchase.PurchaseHistory
import io.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import io.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import io.qonversion.android.sdk.listeners.QonversionLaunchCallback
import io.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import io.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback

internal class RepositoryWithRateLimits(
    private val repository: QRepository,
    private val rateLimiter: RateLimiter,
) : QRepository {
    override fun init(initRequestData: InitRequestData) {
        withRateLimitCheck(
            RequestType.Init,
            initRequestData.hashCode(),
            { error -> initRequestData.callback?.onError(error, null) }
        ) {
            repository.init(initRequestData)
        }
    }

    override fun remoteConfig(userID: String, callback: QonversionRemoteConfigCallback) {
        withRateLimitCheck(
            RequestType.RemoteConfig,
            userID.hashCode(),
            { error -> callback.onError(error) }
        ) {
            repository.remoteConfig(userID, callback)
        }
    }

    override fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        userId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        withRateLimitCheck(
            RequestType.AttachUserToExperiment,
            (experimentId + groupId + userId).hashCode(),
            { error -> callback.onError(error) }
        ) {
            repository.attachUserToExperiment(experimentId, groupId, userId, callback)
        }
    }

    override fun detachUserFromExperiment(
        experimentId: String,
        userId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        withRateLimitCheck(
            RequestType.DetachUserFromExperiment,
            (experimentId + userId).hashCode(),
            { error -> callback.onError(error) }
        ) {
            repository.detachUserFromExperiment(experimentId, userId, callback)
        }
    }

    override fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        userId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        withRateLimitCheck(
            RequestType.AttachUserToRemoteConfiguration,
            (remoteConfigurationId + userId).hashCode(),
            { error -> callback.onError(error) }
        ) {
            repository.attachUserToRemoteConfiguration(remoteConfigurationId, userId, callback)
        }
    }

    override fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        userId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        withRateLimitCheck(
            RequestType.DetachUserFromRemoteConfiguration,
            (remoteConfigurationId + userId).hashCode(),
            { error -> callback.onError(error) }
        ) {
            repository.detachUserFromRemoteConfiguration(remoteConfigurationId, userId, callback)
        }
    }

    override fun purchase(
        installDate: Long,
        purchase: Purchase,
        qProductId: String?,
        callback: QonversionLaunchCallback
    ) {
        withRateLimitCheck(
            RequestType.Purchase,
            purchase.hashCode() + (qProductId + installDate).hashCode(),
            { error -> callback.onError(error, null) }
        ) {
            repository.purchase(installDate, purchase, qProductId, callback)
        }
    }

    override fun restore(
        installDate: Long,
        historyRecords: List<PurchaseHistory>,
        callback: QonversionLaunchCallback?
    ) {
        withRateLimitCheck(
            RequestType.Restore,
            installDate.hashCode() + historyRecords.hashCode(),
            { error -> callback?.onError(error, null) }
        ) {
            repository.restore(installDate, historyRecords, callback)
        }
    }

    override fun attribution(
        conversionInfo: Map<String, Any>,
        from: String,
        onSuccess: (() -> Unit)?,
        onError: ((error: QonversionError) -> Unit)?
    ) {
        withRateLimitCheck(
            RequestType.Attribution,
            conversionInfo.hashCode() + from.hashCode(),
            { error -> onError?.invoke(error) }
        ) {
            repository.attribution(conversionInfo, from, onSuccess, onError)
        }
    }

    override fun sendProperties(
        properties: Map<String, String>,
        onSuccess: (SendPropertiesResult) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        repository.sendProperties(properties, onSuccess, onError)
    }

    override fun getProperties(
        onSuccess: (List<QUserProperty>) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        withRateLimitCheck(
            RequestType.GetProperties,
            0,
            onError,
        ) {
            repository.getProperties(onSuccess, onError)
        }
    }

    override fun eligibilityForProductIds(
        productIds: List<String>,
        installDate: Long,
        callback: QonversionEligibilityCallback
    ) {
        withRateLimitCheck(
            RequestType.EligibilityForProductIds,
            productIds.hashCode() + installDate.hashCode(),
            { error -> callback.onError(error) }
        ) {
            repository.eligibilityForProductIds(productIds, installDate, callback)
        }
    }

    override fun identify(
        userID: String,
        currentUserID: String,
        onSuccess: (identityID: String) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        withRateLimitCheck(
            RequestType.Identify,
            (userID + currentUserID).hashCode(),
            onError,
        ) {
            repository.identify(userID, currentUserID, onSuccess, onError)
        }
    }

    override fun sendPushToken(token: String) {
        repository.sendPushToken(token)
    }

    override fun screens(
        screenId: String,
        onSuccess: (screen: Screen) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        repository.screens(screenId, onSuccess, onError)
    }

    override fun views(screenId: String) {
        repository.views(screenId)
    }

    override fun actionPoints(
        queryParams: Map<String, String>,
        onSuccess: (actionPoint: ActionPointScreen?) -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        repository.actionPoints(queryParams, onSuccess, onError)
    }

    override fun crashReport(
        crashData: CrashRequest,
        onSuccess: () -> Unit,
        onError: (error: QonversionError) -> Unit
    ) {
        repository.crashReport(crashData, onSuccess, onError)
    }

    private fun withRateLimitCheck(
        requestType: RequestType,
        hash: Int,
        onError: (error: QonversionError) -> Unit,
        onSuccess: () -> Unit,
    ) {
        if (rateLimiter.isRateLimitExceeded(requestType, hash)) {
            val error = QonversionError(QonversionErrorCode.ApiRateLimitExceeded)
            onError(error)
        } else {
            rateLimiter.saveRequest(requestType, hash)
            onSuccess()
        }
    }
}
