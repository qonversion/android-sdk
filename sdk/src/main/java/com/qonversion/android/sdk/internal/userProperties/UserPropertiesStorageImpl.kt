package com.qonversion.android.sdk.internal.userProperties

import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.logger.Logger
import java.lang.IllegalStateException

internal class UserPropertiesStorageImpl(
    private val localStorage: LocalStorage,
    private val mapper: MapDataMapper,
    logger: Logger
) : BaseClass(logger), UserPropertiesStorage {

    override val properties: MutableMap<String, String> by lazy {
        getPropertiesFromStorage().toMutableMap()
    }

    override fun set(key: String, value: String) {
        properties[key] = value
        putPropertiesToStorage()
    }

    override fun set(properties: Map<String, String>) {
        this.properties.putAll(properties)
        if (properties.isNotEmpty()) {
            putPropertiesToStorage()
        }
    }

    override fun delete(key: String) {
        properties.remove(key)
        putPropertiesToStorage()
    }

    override fun delete(keys: List<String>) {
        keys.forEach { key ->
            properties.remove(key)
        }
        putPropertiesToStorage()
    }

    fun getPropertiesFromStorage(): Map<String, String> {
        val propertiesJsonString =
            localStorage.getString(StorageConstants.UserProperties.key)

        return if (propertiesJsonString != null) {
            try {
                mapper.toMap(propertiesJsonString)
            } catch (e: IllegalStateException) {
                logger.error("Couldn't load properties from storage", e)
                emptyMap()
            }
        } else emptyMap()
    }

    fun putPropertiesToStorage() {
        try {
            val propertiesJsonString = mapper.fromMap(properties)
            localStorage.putString(StorageConstants.UserProperties.key, propertiesJsonString)
        } catch (e: IllegalStateException) {
            logger.error("Couldn't save properties to storage", e)
        }
    }
}
