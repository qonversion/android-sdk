package com.qonversion.android.sdk.internal.cache.mapper

import com.qonversion.android.sdk.internal.cache.CachedObject
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import java.lang.ClassCastException
import java.lang.Exception
import java.util.*

private const val KEY_TIMESTAMP = "timestamp"
private const val KEY_OBJECT = "object"

internal class CacheMapperImpl<T : Any>(
    private val serializer: Serializer,
    private val mapper: Mapper<T>
) : CacheMapper<T> {

    override fun toString(cachedObject: CachedObject<T>): String {
        val nestedObjectMap = try {
            cachedObject.value?.let {
                mapper.toMap(it)
            }
        } catch (cause: IllegalStateException) {
            throw QonversionException(ErrorCode.Serialization, "Mapper had thrown exception", cause = cause)
        }

        val map = mapOf(
            KEY_TIMESTAMP to cachedObject.date.time,
            KEY_OBJECT to nestedObjectMap
        )

        return serializer.serialize(map)
    }

    override fun fromString(value: String): CachedObject<T> {
        val map = try {
            serializer.deserialize(value) as Map<*, *>
        } catch (cause: ClassCastException) {
            throw QonversionException(ErrorCode.Deserialization, cause = cause)
        }

        try {
            val timestamp = map[KEY_TIMESTAMP] as Long
            val nestedObjectMap = map[KEY_OBJECT] as Map<*, *>?
            val nestedObject = nestedObjectMap?.let {
                try {
                    mapper.fromMap(nestedObjectMap)
                } catch (cause: IllegalStateException) {
                    throw QonversionException(
                        ErrorCode.Deserialization,
                        "Mapper had thrown exception",
                        cause = cause
                    )
                }
            }

            return CachedObject(Date(timestamp), nestedObject)
        } catch (cause: ClassCastException) {
            throw QonversionException(ErrorCode.Deserialization, "Unexpected data type", cause = cause)
        }
    }
}
