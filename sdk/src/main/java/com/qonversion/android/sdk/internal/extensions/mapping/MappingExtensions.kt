package com.qonversion.android.sdk.internal.extensions.mapping

import java.util.Date

fun Map<String, Any?>.getMap(key: String): Map<*, *>? {
    return this[key] as? Map<*, *>
}

fun Map<String, Any?>.getList(key: String): List<*>? {
    return this[key] as? List<*>
}

fun Map<String, Any?>.getBoolean(key: String): Boolean? {
    return this[key] as? Boolean
}

fun Map<String, Any?>.getInt(key: String): Int? {
    return this[key] as? Int
}

fun Map<String, Any?>.getString(key: String): String? {
    return this[key] as? String
}

fun Map<String, Any?>.getFloat(key: String): Float? {
    return this[key] as? Float
}

fun Map<String, Any?>.getDate(key: String): Date? {
    val timestamp: Long? = this[key] as? Long
    var date: Date? = null

    timestamp?.let {
        date = Date(it)
    }

    return date
}
