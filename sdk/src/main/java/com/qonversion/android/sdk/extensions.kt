package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QEntitlement
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun <T> Call<T>.enqueue(callback: CallBackKt<T>.() -> Unit) {
    val callBackKt = CallBackKt<T>()
    callback.invoke(callBackKt)
    this.enqueue(callBackKt)
}

class CallBackKt<T> : Callback<T> {

    var onResponse: ((Response<T>) -> Unit)? = null
    var onFailure: ((t: Throwable?) -> Unit)? = null

    override fun onFailure(call: Call<T>, t: Throwable) {
        onFailure?.invoke(t)
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        onResponse?.invoke(response)
    }
}

fun Int.isInternalServerError() = this in Constants.INTERNAL_SERVER_ERROR_MIN..Constants.INTERNAL_SERVER_ERROR_MAX

internal fun QonversionPermissionsCallback?.toEntitlementsInternalCallback() = object : QonversionEntitlementsCallbackInternal {
    override fun onSuccess(entitlements: List<QEntitlement>) {
        this@toEntitlementsInternalCallback?.onSuccess(entitlements.toPermissionsMap())
    }

    override fun onError(error: QonversionError, responseCode: Int?) {
        this@toEntitlementsInternalCallback?.onError(error)
    }
}

internal fun List<QEntitlement>.toPermissionsMap() = associate { it.permissionID to it.toPermission() }