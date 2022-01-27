package com.qonversion.android.sdk.internal.userProperties

internal interface UserPropertiesStorage {

    val properties: Map<String, String>

    fun set(key: String, value: String)

    fun set(properties: Map<String, String>)

    fun delete(key: String)

    fun delete(keys: List<String>)
}
