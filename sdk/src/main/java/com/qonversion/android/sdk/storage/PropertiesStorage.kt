package com.qonversion.android.sdk.storage

interface PropertiesStorage {
    fun save(key: String, value: String)

    fun clear()

    fun getProperties(): Map<String, String>

}