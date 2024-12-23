package io.qonversion.nocodes.internal.common.serializers

import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.networkLayer.utils.toList
import io.qonversion.nocodes.internal.networkLayer.utils.toMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.NullPointerException

internal class JsonSerializer : Serializer {

    override fun serialize(data: Map<String, Any?>): String {
        return try {
            // toString() might return null on some cases despite the declaration.
            @Suppress("USELESS_ELVIS")
            JSONObject(data).toString() ?: throw NoCodesException(
                ErrorCode.Serialization,
                details = "Json might contain unsupported elements (f.e. null keys)"
            )
        } catch (cause: NullPointerException) {
            throw NoCodesException(
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
                throw NoCodesException(
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
                throw NoCodesException(
                    ErrorCode.Deserialization,
                    "Failed to parse json object",
                    cause
                )
            }
        }
    }
}