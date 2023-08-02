package com.qonversion.android.sdk.internal

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.internal.dto.QPermission
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal fun <T> Call<T>.enqueue(callback: CallBackKt<T>.() -> Unit) {
    val callBackKt = CallBackKt<T>()
    callback.invoke(callBackKt)
    this.enqueue(callBackKt)
}

internal class CallBackKt<T> : Callback<T> {

    var onResponse: ((Response<T>) -> Unit)? = null
    var onFailure: ((t: Throwable) -> Unit)? = null

    override fun onFailure(call: Call<T>, t: Throwable) {
        onFailure?.invoke(t)
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        onResponse?.invoke(response)
    }
}

internal fun Int.isInternalServerError() =
    this in Constants.INTERNAL_SERVER_ERROR_MIN..Constants.INTERNAL_SERVER_ERROR_MAX

internal fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        var value: Any? = get(key)
        when {
            isNull(key) -> {
                value = null
            }
            value is JSONArray -> {
                value = value.toList()
            }
            value is JSONObject -> {
                value = value.toMap()
            }
        }
        map[key] = value
    }
    return map
}

internal fun JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until length()) {
        var value: Any? = get(i)
        when {
            isNull(i) -> {
                value = null
            }
            value is JSONArray -> {
                value = value.toList()
            }
            value is JSONObject -> {
                value = value.toMap()
            }
        }
        list.add(value)
    }
    return list
}

internal val Context.isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

internal val Context.application get() = applicationContext as Application

internal fun Int.toBoolean() = this != 0

internal fun String?.toBoolean() = this == "1"

internal fun Boolean.toInt() = if (this) 1 else 0

internal fun Boolean.stringValue() = if (this) "1" else "0"

internal fun Long.milliSecondsToSeconds(): Long = this / 1000

internal fun Long.secondsToMilliSeconds(): Long = this * 1000

internal fun Map<String, QPermission>.toEntitlementsMap(): Map<String, QEntitlement> {
    val res = mutableMapOf<String, QEntitlement>()
    forEach { (id, permission) -> res[id] = QEntitlement(permission) }
    return res
}

internal infix fun <T> List<T>.equalsIgnoreOrder(other: List<T>) =
    this.size == other.size && this.toSet() == other.toSet()
