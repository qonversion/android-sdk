package com.qonversion.android.sdk.storage

interface PropertiesStorage {
    fun save(key: String, value: String)

    fun clear(properties: Map<String, String>)

    fun getProperties(): Map<String, String>

    fun getHandledProperties(): Map<String, String>

    fun saveHandledProperties(properties: Map<String, String>)
}
