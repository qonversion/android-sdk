package io.qonversion.android.sdk.listeners

import io.qonversion.android.sdk.dto.entitlements.QEntitlement
import io.qonversion.android.sdk.dto.QRemoteConfig
import io.qonversion.android.sdk.dto.QonversionError
import io.qonversion.android.sdk.internal.dto.QLaunchResult
import io.qonversion.android.sdk.dto.QUser
import io.qonversion.android.sdk.dto.offerings.QOfferings
import io.qonversion.android.sdk.dto.products.QProduct
import io.qonversion.android.sdk.dto.eligibility.QEligibility
import io.qonversion.android.sdk.dto.properties.QUserProperties

internal interface QonversionLaunchCallback {
   fun onSuccess(launchResult: QLaunchResult)
   fun onError(error: QonversionError, httpCode: Int?)
}

interface QonversionProductsCallback {
   fun onSuccess(products: Map<String, QProduct>)
   fun onError(error: QonversionError)
}

interface QonversionOfferingsCallback {
   fun onSuccess(offerings: QOfferings)
   fun onError(error: QonversionError)
}

interface QonversionRemoteConfigCallback {
   fun onSuccess(remoteConfig: QRemoteConfig)
   fun onError(error: QonversionError)
}

interface QonversionExperimentAttachCallback {
   fun onSuccess()
   fun onError(error: QonversionError)
}

interface QonversionRemoteConfigurationAttachCallback {
   fun onSuccess()
   fun onError(error: QonversionError)
}

interface QonversionEntitlementsCallback {
   fun onSuccess(entitlements: Map<String, QEntitlement>)
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

interface QonversionUserCallback {
   fun onSuccess(user: QUser)
   fun onError(error: QonversionError)
}

interface QonversionUserPropertiesCallback {
   fun onSuccess(userProperties: QUserProperties)
   fun onError(error: QonversionError)
}
