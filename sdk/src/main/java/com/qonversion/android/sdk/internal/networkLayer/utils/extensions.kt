package com.qonversion.android.sdk.internal.networkLayer.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Throws(JSONException::class)
fun JSONObject.toMap(): Map<String, Any> {
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
