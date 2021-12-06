package com.qonversion.android.sdk.internal.networkLayer.requestSerializer

import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.utils.toList
import com.qonversion.android.sdk.internal.networkLayer.utils.toMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.NullPointerException

internal class JsonSerializer : RequestSerializer {

    override fun serialize(data: Map<String, Any?>): String {
        return try {
            // toString() might return null on some cases, which are not documented.
            JSONObject(data).toString() ?: throw QonversionException(
                ErrorCode.Serialization,
                details = "Json might contain unsupported elements (f.e. null keys)"
            )
        } catch (cause: NullPointerException) {
            throw QonversionException(
                ErrorCode.Serialization,
                cause = cause
            )
        }
    }

    override fun deserialize(payload: String): Any {
        return try {
            val array = JSONArray(payload)
            try {
                array.toList()
            } catch (cause: JSONException) {
                throw QonversionException(
                    ErrorCode.Deserialization,
                    "Failed to parse json array",
                    cause
                )
            }
        } catch (_: JSONException) {
            // If we caught the exception above
            // then there was not an array in payload,
            // so we should try to parse as object.
            try {
                val obj = JSONObject(payload)
                obj.toMap()
            } catch (cause: JSONException) {
                throw QonversionException(
                    ErrorCode.Deserialization,
                    "Failed to parse json object",
                    cause
                )
            }
        }
    }
}
