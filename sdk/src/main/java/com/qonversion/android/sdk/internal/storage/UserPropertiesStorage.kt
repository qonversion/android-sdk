package com.qonversion.android.sdk.internal.storage

import java.util.concurrent.ConcurrentHashMap

internal class UserPropertiesStorage : PropertiesStorage {
    private val userProperties: MutableMap<String, String> =
        ConcurrentHashMap()

    override fun save(key: String, value: String) {
        userProperties[key] = value
    }

    override fun clear(properties: Map<String, String>) {
        properties.keys.map {
            userProperties.remove(it)
        }
    }

    override fun getProperties(): Map<String, String> {
        return userProperties.toMap()
    }
}
