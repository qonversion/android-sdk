package com.qonversion.android.sdk.internal.cache.mapper

import com.qonversion.android.sdk.internal.cache.CachedObject
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface CacheMapper<T : Any> {

    @Throws(QonversionException::class)
    fun toString(cachedObject: CachedObject<T>): String

    @Throws(QonversionException::class)
    fun fromString(value: String): CachedObject<T>
}
