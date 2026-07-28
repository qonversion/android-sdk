package com.qonversion.android.sdk.internal.storage

import java.util.concurrent.ConcurrentHashMap

internal class UserPropertiesStorage : PropertiesStorage {
    private val userProperties: ConcurrentHashMap<String, String> =
        ConcurrentHashMap()

    override fun save(key: String, value: String) {
        userProperties[key] = value
    }

    override fun clear(properties: Map<String, String>) {
        // Value-conditional removal: a key overwritten while its previous value
        // was in flight must survive the post-send cleanup, or the new value
        // would be silently lost. Unchanged values are still removed so invalid
        // ones are not resent.
        properties.forEach { (key, value) ->
            userProperties.remove(key, value)
        }
    }

    override fun getProperties(): Map<String, String> {
        return userProperties.toMap()
    }
}
