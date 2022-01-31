package com.qonversion.android.sdk.internal.userProperties

internal interface UserPropertiesStorage {

    val properties: Map<String, String>

    fun add(key: String, value: String)

    fun add(properties: Map<String, String>)

    fun delete(key: String, value: String)

    fun delete(properties: Map<String, String>)
}
