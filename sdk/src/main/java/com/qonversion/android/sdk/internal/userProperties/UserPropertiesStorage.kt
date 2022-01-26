package com.qonversion.android.sdk.internal.userProperties

internal interface UserPropertiesStorage {

    val pendingProperties: Map<String, String>

    fun save(key: String, value: String)

    fun delete(key: String)

    fun update(key: String, newValue: String)
}
