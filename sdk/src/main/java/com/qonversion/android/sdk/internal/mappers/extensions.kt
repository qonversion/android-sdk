package com.qonversion.android.sdk.internal.mappers

import java.util.Date

fun Map<*, *>.getMap(key: String): Map<*, *>? {
    return this[key] as? Map<*, *>
}

fun Map<*, *>.getList(key: String): List<*>? {
    return this[key] as? List<*>
}

fun Map<*, *>.getBoolean(key: String): Boolean? {
    return this[key] as? Boolean
}

fun Map<*, *>.getInt(key: String): Int? {
    return this[key] as? Int
}

fun Map<*, *>.getString(key: String): String? {
    return this[key] as? String
}

fun Map<*, *>.getFloat(key: String): Float? {
    return this[key] as? Float
}

fun Map<*, *>.getDate(key: String): Date? {
    val timestamp: Long? = this[key] as? Long
    var date: Date? = null

    timestamp?.let {
        date = Date(it)
    }

    return date
}
