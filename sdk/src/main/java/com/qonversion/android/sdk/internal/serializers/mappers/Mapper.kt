package com.qonversion.android.sdk.internal.serializers.mappers

internal interface Mapper<T : Any> {
    @Throws(IllegalStateException::class, NotImplementedError::class)
    fun toMap(value: T): Map<String, Any?> { throw NotImplementedError() }

    @Throws(IllegalStateException::class)
    fun fromMap(data: Map<*, *>): T?

    @Throws(IllegalStateException::class)
    fun fromList(data: List<*>): List<T> {
        return data.mapNotNull { item ->
            (item as? Map<*, *>)?.let {
                fromMap(it)
            }
        }
    }
}
