package com.qonversion.android.sdk.internal.cache.mapper

import com.qonversion.android.sdk.internal.cache.CachedObject
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface CacheMapper<T> {

    @Throws(QonversionException::class)
    fun toSerializedString(cachedObject: CachedObject<T>): String

    @Throws(QonversionException::class)
    fun fromSerializedString(value: String): CachedObject<T>
}
