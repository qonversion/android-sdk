package com.qonversion.android.sdk

import org.json.JSONArray
import org.json.JSONException
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
    var onFailure: ((t: Throwable?) -> Unit)? = null

    override fun onFailure(call: Call<T>, t: Throwable) {
        onFailure?.invoke(t)
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        onResponse?.invoke(response)
    }
}

@Throws(JSONException::class)
fun JSONObject.toMap(): Map<String, Any>  {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key).parseJsonValue()
        map[key] = value
    }

    return map
}

@Throws(JSONException::class)
fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        val value = get(i).parseJsonValue()
        list.add(value)
    }

    return list.toList()
}

@Throws(JSONException::class)
private fun Any.parseJsonValue() = when (this) {
    is JSONArray -> toList()
    is JSONObject -> toMap()
    else -> this
}
