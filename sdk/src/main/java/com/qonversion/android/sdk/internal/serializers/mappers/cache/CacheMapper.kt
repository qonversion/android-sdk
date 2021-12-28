package com.qonversion.android.sdk.internal.serializers.mappers.cache

import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface CacheMapper<T : Any> {

    @Throws(QonversionException::class)
    fun toJson(value: T): String

    @Throws(QonversionException::class)
    fun fromJson(json: String): T?
}
