package com.qonversion.android.sdk.internal.common.serializers

import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface Serializer {

    @Throws(QonversionException::class)
    fun serialize(data: Map<String, Any?>): String

    @Throws(QonversionException::class)
    fun deserialize(payload: String): Any
}
