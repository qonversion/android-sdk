package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface Cacher<T : Any> {

    @Throws(QonversionException::class)
    fun store(key: String, value: T)

    @Throws(QonversionException::class)
    fun get(key: String): T?

    @Throws(QonversionException::class)
    fun getActual(key: String): T?

    @Throws(QonversionException::class)
    fun reset(key: String)
}
