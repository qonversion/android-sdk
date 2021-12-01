package com.qonversion.android.sdk.internal.networkLayer.requestSerializer

import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface RequestSerializer {

    @Throws(QonversionException::class)
    fun serialize(data: Map<String, Any?>): String

    @Throws(QonversionException::class)
    fun deserialize(payload: String): Any
}
