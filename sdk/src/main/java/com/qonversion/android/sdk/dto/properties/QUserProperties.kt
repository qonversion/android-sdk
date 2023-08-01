package com.qonversion.android.sdk.dto.properties

import com.qonversion.android.sdk.Qonversion

data class QUserProperties(
    /**
     * List of all user properties.
     */
    val properties: List<QUserProperty>
) {
    /**
     * List of user properties, set for the Qonversion defined keys.
     * This is a subset of all [properties] list.
     * @see [Qonversion.setUserProperty]
     */
    val definedProperties: List<QUserProperty> = properties
        .filter { it.definedKey !== QUserPropertyKey.Custom }

    /**
     * List of user properties, set for custom keys.
     * This is a subset of all [properties] list.
     * @see [Qonversion.setCustomUserProperty]
     */
    val customProperties: List<QUserProperty> = properties
        .filter { it.definedKey === QUserPropertyKey.Custom }

    /**
     * Map of all user properties.
     * This is a flattened version of the [properties] list as a key-value map.
     */
    val flatPropertiesMap: Map<String, String> = properties
        .associate { it.key to it.value }

    /**
     * Map of user properties, set for the Qonversion defined keys.
     * This is a flattened version of the [definedProperties] list as a key-value map.
     * @see [Qonversion.setUserProperty]
     */
    val flatDefinedPropertiesMap: Map<QUserPropertyKey, String> = definedProperties
        .associate { it.definedKey to it.value }

    /**
     * Map of user properties, set for custom keys.
     * This is a flattened version of the [customProperties] list as a key-value map.
     * @see [Qonversion.setCustomUserProperty]
     */
    val flatCustomPropertiesMap: Map<String, String> = customProperties
        .associate { it.key to it.value }

    /**
     * Searches for a property with the given property [key] in all properties list.
     */
    fun getProperty(key: String): QUserProperty? =
        properties.find { it.key == key }

    /**
     * Searches for a property with the given Qonversion defined property [key]
     * in defined properties list.
     */
    fun getDefinedProperty(key: QUserPropertyKey): QUserProperty? =
        definedProperties.find { it.definedKey === key }
}
