package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QProduct

interface QonversionCallback {
   fun onSuccess(uid: String)
   fun onError(t: Throwable)
}

interface QonversionLaunchCallback {
   fun onSuccess(launchResult: QLaunchResult)
   fun onError(t: Throwable)
}

interface QonversionProductsCallback {
   fun onSuccess(products: Map<String, QProduct>)
   fun onError(t: Throwable)
}

interface QonversionPermissionsCallback {
   fun onSuccess(permissions: Map<String, QPermission>)
   fun onError(t: Throwable)
}

interface QonversionPurchasesCallback {
   fun onSuccess(uid: String)
   fun onError(t: Throwable)
}