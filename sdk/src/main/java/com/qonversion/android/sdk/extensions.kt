package com.qonversion.android.sdk

import org.json.JSONArray
import org.json.JSONObject
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
    var onFailure: ((t: Throwable) -> Unit)? = null

    override fun onFailure(call: Call<T>, t: Throwable) {
        onFailure?.invoke(t)
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        onResponse?.invoke(response)
    }
}

fun Int.isInternalServerError() = this in Constants.INTERNAL_SERVER_ERROR_MIN..Constants.INTERNAL_SERVER_ERROR_MAX

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