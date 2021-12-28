package com.qonversion.android.sdk.internal.serializers.mappers.cache

import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.serializers.json.JsonSerializer
import com.qonversion.android.sdk.internal.serializers.mappers.Mapper
import java.lang.ClassCastException

internal class CacheMapperImpl<T : Any>(
    private val jsonSerializer: JsonSerializer,
    private val mapper: Mapper<T>
) : CacheMapper<T> {

    override fun toJson(value: T): String {
        val map = try {
            mapper.toMap(value)
        } catch (cause: IllegalStateException) {
            throw QonversionException(ErrorCode.Serialization, "Mapper had thrown exception", cause = cause)
        }
        return jsonSerializer.serialize(map)
    }

    override fun fromJson(json: String): T? {
        val map = try {
            jsonSerializer.deserialize(json) as Map<*, *>
        } catch (cause: ClassCastException) {
            throw QonversionException(ErrorCode.Deserialization, cause = cause)
        }
        return try {
            mapper.fromMap(map)
        } catch (cause: IllegalStateException) {
            throw QonversionException(ErrorCode.Deserialization, "Mapper had thrown exception", cause = cause)
        }
    }
}
