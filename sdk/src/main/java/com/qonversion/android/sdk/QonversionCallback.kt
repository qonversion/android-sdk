package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.*

interface QonversionLaunchCallback {
   fun onSuccess(launchResult: QLaunchResult)
   fun onError(error: QonversionError)
}

interface QonversionProductsCallback {
   fun onSuccess(products: Map<String, QProduct>)
   fun onError(error: QonversionError)
}

interface QonversionPermissionsCallback {
   fun onSuccess(permissions: Map<String, QPermission>)
   fun onError(error: QonversionError)
}

interface QonversionScreensCallback {
   fun onSuccess(htmlPage: String)
   fun onError(error: QonversionError)
}