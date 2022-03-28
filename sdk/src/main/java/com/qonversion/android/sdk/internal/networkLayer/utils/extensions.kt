package com.qonversion.android.sdk.internal.networkLayer.utils

import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Throws(JSONException::class)
internal fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key).parseJsonValue()
        map[key] = value
    }

    return map
}

@Throws(JSONException::class)
internal fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        val value = get(i).parseJsonValue()
        list.add(value)
    }

    return list.toList()
}

@Throws(JSONException::class)
@VisibleForTesting
internal fun Any.parseJsonValue() = when (this) {
    is JSONArray -> toList()
    is JSONObject -> toMap()
    else -> this
}

private const val MIN_SUCCESS_CODE = 200
private const val MAX_SUCCESS_CODE = 299
private const val MIN_INTERNAL_SERVER_ERROR_CODE = 500
private const val MAX_INTERNAL_SERVER_ERROR_CODE = 599

internal val Int.isSuccessHttpCode: Boolean get() = this in MIN_SUCCESS_CODE..MAX_SUCCESS_CODE
internal val Int.isInternalServerErrorHttpCode: Boolean get() =
    this in MIN_INTERNAL_SERVER_ERROR_CODE..MAX_INTERNAL_SERVER_ERROR_CODE
