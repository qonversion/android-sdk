package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QOfferings
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QProduct
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.QExperimentInfo

interface QonversionLaunchCallback {
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