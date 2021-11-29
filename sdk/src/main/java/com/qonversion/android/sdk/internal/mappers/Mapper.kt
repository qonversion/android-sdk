package com.qonversion.android.sdk.internal.mappers

interface Mapper<T> {
    @Throws(IllegalStateException::class)
    fun toMap(value: T): Map<String, Any?>

    @Throws(IllegalStateException::class)
    fun fromMap(data: Map<String, Any?>): T
}
