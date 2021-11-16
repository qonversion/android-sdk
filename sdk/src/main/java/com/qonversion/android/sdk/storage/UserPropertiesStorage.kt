package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.Constants.HANDLED_PROPERTIES_KEY
import com.qonversion.android.sdk.toMap
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class UserPropertiesStorage @Inject internal constructor(
    private val preferences: SharedPreferencesCache
) : PropertiesStorage {
    private val userProperties: MutableMap<String, String> =
        ConcurrentHashMap()

    override fun save(key: String, value: String) {
        userProperties[key] = value
    }

    override fun clear(properties: Map<String, String>) {
        properties.keys.map {
            if (userProperties[it] == properties[it]) {
                userProperties.remove(it)
            }
        }
    }

    override fun getProperties(): Map<String, String> {
        return userProperties.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    override fun getHandledProperties(): Map<String, String> {
        return try {
            val jsonString: String = preferences.getString(HANDLED_PROPERTIES_KEY, null) ?: ""
            val jsonObject = JSONObject(jsonString)

            jsonObject.toMap() as Map<String, String>
        } catch (commonException: Exception) {
            mapOf()
        }
    }

    override fun saveHandledProperties(properties: Map<String, String>) {
        val jsonString: String = JSONObject(properties).toString()
        preferences.putString(HANDLED_PROPERTIES_KEY, jsonString)
    }
}
