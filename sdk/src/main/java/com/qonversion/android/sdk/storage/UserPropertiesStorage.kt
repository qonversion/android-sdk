package com.qonversion.android.sdk.storage

import java.util.concurrent.ConcurrentHashMap

class UserPropertiesStorage : PropertiesStorage {
    private val userProperties: MutableMap<String, String> =
        ConcurrentHashMap()

    override fun save(key: String, value: String) {
        userProperties[key] = value
    }

    override fun clear() {
        userProperties.clear()
    }

    override fun getProperties(): MutableMap<String, String> {
        return userProperties
    }
}