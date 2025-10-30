package com.qonversion.android.sdk.internal.purchase

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseResultCallback

interface InternalPurchaseCallback {
    fun onSuccess(result: QPurchaseResult)
    fun onCancelled(result: QPurchaseResult)
    fun onError(error: QonversionError, result: QPurchaseResult?)
}

object InternalPurchaseCallbackFactory {
    fun from(callback: QonversionPurchaseResultCallback): InternalPurchaseCallback = object : InternalPurchaseCallback {
        override fun onSuccess(result: QPurchaseResult) {
            callback.onResult(result)
        }
        override fun onCancelled(result: QPurchaseResult) {
            callback.onResult(result)
        }
        override fun onError(error: QonversionError, result: QPurchaseResult?) {
            val out = result ?: QPurchaseResult(error, false)
            callback.onResult(out)
        }
    }

    fun from(callback: QonversionEntitlementsCallback): InternalPurchaseCallback = object : InternalPurchaseCallback {
        override fun onSuccess(result: QPurchaseResult) {
            callback.onSuccess(result.entitlements)
        }
        override fun onCancelled(result: QPurchaseResult) {
            // для legacy колбэка сигнализируем отмену как ошибку PurchaseCanceled
            callback.onError(QonversionError(code = com.qonversion.android.sdk.dto.QonversionErrorCode.PurchaseCanceled))
        }
        override fun onError(error: QonversionError, result: QPurchaseResult?) {
            callback.onError(error)
        }
    }
}
