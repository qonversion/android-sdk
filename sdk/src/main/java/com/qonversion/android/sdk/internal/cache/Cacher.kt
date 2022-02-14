package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface Cacher<T : Any> {

    @Throws(QonversionException::class)
    fun store(value: T)

    @Throws(QonversionException::class)
    fun get(): T?

    @Throws(QonversionException::class)
    fun getActual(cacheState: CacheState = CacheState.Default): T?

    @Throws(QonversionException::class)
    fun reset()
}
