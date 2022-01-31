package com.qonversion.android.sdk.internal.userProperties

import androidx.annotation.VisibleForTesting
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

    override fun add(key: String, value: String) {
        properties[key] = value
        putPropertiesToStorage()
    }

    override fun add(properties: Map<String, String>) {
        this.properties.putAll(properties)
        if (properties.isNotEmpty()) {
            putPropertiesToStorage()
        }
    }

    override fun delete(key: String, value: String) {
        val localStoredValue = this.properties[key]
        if (localStoredValue == value) {
            properties.remove(key)
            putPropertiesToStorage()
        }
    }

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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun putPropertiesToStorage() {
        try {
            val propertiesJsonString = mapper.fromMap(properties)
            localStorage.putString(StorageConstants.UserProperties.key, propertiesJsonString)
        } catch (e: IllegalStateException) {
            logger.error("Couldn't save properties to storage", e)
        }
    }
}
