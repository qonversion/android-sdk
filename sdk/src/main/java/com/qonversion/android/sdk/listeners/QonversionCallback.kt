package com.qonversion.android.sdk.listeners

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.properties.QUserProperties
import com.qonversion.android.sdk.dto.QPurchaseResult

internal interface QonversionLaunchCallback {
   fun onSuccess(launchResult: QLaunchResult)
   fun onError(error: QonversionError)
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

interface QonversionRemoteConfigListCallback {
   fun onSuccess(remoteConfigList: QRemoteConfigList)
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

interface QonversionPurchaseCallback : QonversionEntitlementsCallback {
   fun onSuccess(entitlements: Map<String, QEntitlement>, purchase: Purchase) = onSuccess(entitlements)
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

interface QonversionEmptyCallback {

   fun onComplete()
}

/**
 * Callback interface for purchase operations that return a single PurchaseResult object.
 * This is the new recommended way to handle purchase results as it provides
 * all relevant information in a single object.
 * 
 * @see QPurchaseResult for details about the result object
 */
interface QonversionPurchaseResultCallback {
    /**
     * Called when the purchase operation completes successfully
     * @param result PurchaseResult containing entitlements and purchase details
     */
    fun onSuccess(result: QPurchaseResult)
    
    /**
     * Called when the purchase operation fails
     * @param result PurchaseResult containing error information
     */
    fun onError(result: QPurchaseResult)
}
