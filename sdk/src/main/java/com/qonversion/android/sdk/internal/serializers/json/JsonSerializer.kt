package com.qonversion.android.sdk.internal.serializers.json

import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface JsonSerializer {

    @Throws(QonversionException::class)
    fun serialize(data: Map<String, Any?>): String

    @Throws(QonversionException::class)
    fun deserialize(payload: String): Any
}
