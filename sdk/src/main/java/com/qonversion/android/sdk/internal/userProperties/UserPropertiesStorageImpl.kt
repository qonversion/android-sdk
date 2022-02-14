package com.qonversion.android.sdk.internal.userProperties

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.logger.Logger
import java.lang.IllegalStateException

internal class UserPropertiesStorageImpl(
    private val localStorage: LocalStorage,
    private val mapper: Mapper<String>,
    private val key: String, // TODO update for identity flow, add user id provider
    logger: Logger
) : BaseClass(logger), UserPropertiesStorage {

    override val properties: MutableMap<String, String> by lazy {
        getPropertiesFromStorage().toMutableMap()
    }

    @Synchronized
    override fun add(key: String, value: String) {
        properties[key] = value
        putPropertiesToStorage()
    }

    @Synchronized
    override fun add(properties: Map<String, String>) {
        if (properties.isNotEmpty()) {
            this.properties.putAll(properties)
            putPropertiesToStorage()
        }
    }

    @Synchronized
    override fun delete(key: String, value: String) {
        val localStoredValue = properties[key]
        if (localStoredValue == value) {
            properties.remove(key)
            putPropertiesToStorage()
        }
    }

    @Synchronized
    override fun delete(properties: Map<String, String>) {
        properties.forEach { (key, value) ->
            val localStoredValue = this.properties[key]

            if (value == localStoredValue) {
                this.properties.remove(key)
            }
        }

        putPropertiesToStorage()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getPropertiesFromStorage(): Map<String, String> {
        val propertiesJsonString =
            localStorage.getString(key)

        return if (propertiesJsonString != null) {
            try {
                val properties = mapper.toMap(propertiesJsonString)
                properties.mapValues { it.value.toString() }
            } catch (e: IllegalStateException) {
                logger.error("Couldn't load properties from storage", e)
                emptyMap()
            }
        } else emptyMap()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun putPropertiesToStorage() {
        try {
            val propertiesJsonString = mapper.fromMap(properties)
            localStorage.putString(key, propertiesJsonString)
        } catch (e: IllegalStateException) {
            logger.error("Couldn't save properties to storage", e)
        }
    }
}
