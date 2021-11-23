package com.qonversion.android.sdk.old

import com.qonversion.android.sdk.old.dto.QLaunchResult
import com.qonversion.android.sdk.old.dto.offerings.QOfferings
import com.qonversion.android.sdk.old.dto.QPermission
import com.qonversion.android.sdk.old.dto.products.QProduct
import com.qonversion.android.sdk.old.dto.eligibility.QEligibility
import com.qonversion.android.sdk.old.dto.experiments.QExperimentInfo

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

interface QonversionShowScreenCallback {
   fun onSuccess()
   fun onError(error: QonversionError)
}
