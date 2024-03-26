package com.qonversion.android.sdk.internal.repository

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.properties.QUserProperty
import com.qonversion.android.sdk.internal.dto.SendPropertiesResult
import com.qonversion.android.sdk.internal.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.internal.dto.automations.Screen
import com.qonversion.android.sdk.internal.dto.request.CrashRequest
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback

internal interface QRepository {

    fun init(initRequestData: InitRequestData)

    fun remoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback)

    fun remoteConfigList(contextKeys: List<String>, withEmptyContextKey: Boolean, callback: QonversionRemoteConfigListCallback)

    fun remoteConfigList(callback: QonversionRemoteConfigListCallback)

    fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        callback: QonversionExperimentAttachCallback
    )

    fun detachUserFromExperiment(
        experimentId: String,
        callback: QonversionExperimentAttachCallback
    )

    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    )

    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    )

    fun purchase(
        installDate: Long,
        purchase: Purchase,
        qProductId: String?,
        callback: QonversionLaunchCallback
    )

    fun restore(
        installDate: Long,
        historyRecords: List<PurchaseHistory>,
        callback: QonversionLaunchCallback?
    )

    fun attribution(
        conversionInfo: Map<String, Any>,
        from: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((error: QonversionError) -> Unit)? = null
    )

    fun sendProperties(
        properties: Map<String, String>,
        onSuccess: (SendPropertiesResult) -> Unit,
        onError: (error: QonversionError) -> Unit
    )

    fun getProperties(
        onSuccess: (List<QUserProperty>) -> Unit,
        onError: (error: QonversionError) -> Unit
    )

    fun eligibilityForProductIds(
        productIds: List<String>,
        installDate: Long,
        callback: QonversionEligibilityCallback
    )

    fun identify(
        userID: String,
        currentUserID: String,
        onSuccess: (identityID: String) -> Unit,
        onError: (error: QonversionError) -> Unit
    )

    fun sendPushToken(token: String)

    fun screens(
        screenId: String,
        onSuccess: (screen: Screen) -> Unit,
        onError: (error: QonversionError) -> Unit
    )

    fun views(screenId: String)

    fun actionPoints(
        queryParams: Map<String, String>,
        onSuccess: (actionPoint: ActionPointScreen?) -> Unit,
        onError: (error: QonversionError) -> Unit
    )

    fun crashReport(
        crashData: CrashRequest,
        onSuccess: () -> Unit,
        onError: (error: QonversionError) -> Unit
    )
}
