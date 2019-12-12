package com.qonversion.android.sdk

interface QonversionCallback {
   fun onSuccess(uid: String)
   fun onError(t: Throwable)
}