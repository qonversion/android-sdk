package com.qonversion.android.sdk.internal.networkLayer.requestSerializer

import com.qonversion.android.sdk.internal.networkLayer.utils.toList
import com.qonversion.android.sdk.internal.networkLayer.utils.toMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class JsonSerializer: RequestSerializer {

    override fun serialize(data: Map<String, Any?>): String {
        return JSONObject(data).toString()
    }

    override fun deserialize(payload: String): Any {
        return try {
            val array = JSONArray(payload)
            array.toList()
        } catch (_: JSONException) {
            val obj = JSONObject(payload)
            obj.toMap()
        }
    }
}
