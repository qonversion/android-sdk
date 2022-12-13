package com.qonversion.android.sdk.internal.storage

internal interface PropertiesStorage {
    fun save(key: String, value: String)

    fun clear(properties: Map<String, String>)

    fun getProperties(): Map<String, String>
}
