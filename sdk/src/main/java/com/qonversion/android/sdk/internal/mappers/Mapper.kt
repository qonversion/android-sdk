package com.qonversion.android.sdk.internal.mappers

interface Mapper<T> {
    @Throws(IllegalStateException::class)
    fun toMap(value: T): Map<String, Any?> { return emptyMap() }

    @Throws(IllegalStateException::class)
    fun fromMap(data: Map<String, Any?>): T?

    @Throws(IllegalStateException::class)
    fun arrayFromMap(data: Map<String, Any?>): List<T> { return emptyList() }
}
