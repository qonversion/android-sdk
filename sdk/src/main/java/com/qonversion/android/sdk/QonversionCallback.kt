package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QRestoreResult
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.experiments.QExperimentInfo

interface QonversionLaunchCallback {
    fun onSuccess(launchResult: QLaunchResult)
    fun onError(error: QonversionError)
}

interface QonversionRestoreCallback {
    fun onSuccess(restoreResult: QRestoreResult)
    fun onError(error: QonversionError)
}

interface QonversionProductsCallback {
    fun onSuccess(products: Map<String, QProduct>)
    fun onError(error: QonversionError)
}

interface QonversionEntitlementsCallback {
    fun onSuccess(entitlements: List<QEntitlement>)
    fun onError(error: QonversionError)
}

internal interface QonversionEntitlementsCallbackInternal {
    fun onSuccess(entitlements: List<QEntitlement>)
    fun onLoadedFromCache(entitlements: List<QEntitlement>, requestError: QonversionError) =
        onSuccess(entitlements)

    fun onError(error: QonversionError, responseCode: Int?)
}

interface QonversionOfferingsCallback {
    fun onSuccess(offerings: QOfferings)
    fun onError(error: QonversionError)
}

interface QonversionExperimentsCallback {
    fun onSuccess(experiments: Map<String, QExperimentInfo>)
    fun onError(error: QonversionError)
}

interface QonversionPermissionsCallback {
    fun onSuccess(permissions: Map<String, QPermission>)
    fun onError(error: QonversionError)
}

interface QonversionEligibilityCallback {
    fun onSuccess(eligibilities: Map<String, QEligibility>)
    fun onError(error: QonversionError)
}

interface QonversionShowScreenCallback {
    fun onSuccess()
    fun onError(error: QonversionError)
}
