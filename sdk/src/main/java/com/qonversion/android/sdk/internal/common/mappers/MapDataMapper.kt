package com.qonversion.android.sdk.internal.common.mappers

import com.qonversion.android.sdk.internal.networkLayer.utils.toMap
import org.json.JSONException
import org.json.JSONObject

internal class MapDataMapper : Mapper<String> {
    override fun fromMap(data: Map<*, *>): String {
        try {
            val propertiesJsonObj = JSONObject(data)
            return propertiesJsonObj.toString()
        } catch (e: NullPointerException) {
            throw IllegalStateException("Couldn't create JSONObject from map", e)
        }
    }

    override fun toMap(value: String): Map<String, Any?> {
        try {
            val propertiesJsonObj = JSONObject(value)
            return propertiesJsonObj.toMap()
        } catch (e: JSONException) {
            throw IllegalStateException("Couldn't create JSONObject from string", e)
        }
    }
}
