package com.qonversion.android.sdk.old.storage

interface PropertiesStorage {
    fun save(key: String, value: String)

    fun clear(properties: Map<String, String>)

    fun getProperties(): Map<String, String>
}
