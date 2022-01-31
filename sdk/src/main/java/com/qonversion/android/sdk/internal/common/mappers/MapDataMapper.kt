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

    override fun toMap(value: String): Map<String, String> {
        try {
            val propertiesJsonObj = JSONObject(value)
            val propertiesMap = propertiesJsonObj.toMap()
            return propertiesMap.mapValues { it.value.toString() }
        } catch (e: JSONException) {
            throw IllegalStateException("Couldn't create JSONObject from string", e)
        }
    }
}
