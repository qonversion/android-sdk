package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface Cacher<T> {

    @Throws(QonversionException::class)
    fun store(value: T)

    @Throws(QonversionException::class)
    fun getStoredValue(): T?

    @Throws(QonversionException::class)
    fun getActualStoredValue(cacheState: CacheState = CacheState.Default): T?

    @Throws(QonversionException::class)
    fun reset()
}
