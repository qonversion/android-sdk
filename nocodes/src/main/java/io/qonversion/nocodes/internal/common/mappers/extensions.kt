package io.qonversion.nocodes.internal.common.mappers

import java.util.Date

internal fun Map<*, *>.getMap(key: String): Map<*, *>? {
    return this[key] as? Map<*, *>
}

internal fun Map<*, *>.getList(key: String): List<*>? {
    return this[key] as? List<*>
}

internal fun Map<*, *>.getBoolean(key: String): Boolean? {
    return this[key] as? Boolean
}

internal fun Map<*, *>.getInt(key: String): Int? {
    return this[key] as? Int
}

internal fun Map<*, *>.getString(key: String): String? {
    return this[key] as? String
}

internal fun Map<*, *>.getFloat(key: String): Float? {
    return this[key] as? Float
}

internal fun Map<*, *>.getDate(key: String): Date? {
    val timestamp: Long? = this[key] as? Long

    return timestamp?.let {
        Date(it)
    }
}