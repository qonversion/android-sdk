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
     * @see [Qonversion.setUserProperty]
     */
    val definedProperties: List<QUserProperty> = properties
        .filter { it.definedKey !== QUserPropertyKey.Custom }

    /**
     * List of user properties, set for custom keys.
     * @see [Qonversion.setCustomUserProperty]
     */
    val customProperties: List<QUserProperty> = properties
        .filter { it.definedKey === QUserPropertyKey.Custom }

    /**
     * Map of all user properties.
     */
    val propertiesMap: Map<String, String> = properties
        .associate { it.key to it.value }

    /**
     * Map of user properties, set for the Qonversion defined keys.
     * @see [Qonversion.setUserProperty]
     */
    val definedPropertiesMap: Map<QUserPropertyKey, String> = definedProperties
        .associate { it.definedKey to it.value }

    /**
     * Map of user properties, set for custom keys.
     * @see [Qonversion.setCustomUserProperty]
     */
    val customPropertiesMap: Map<String, String> = customProperties
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

    /**
     * Searches for a property with the given custom property [key] in custom properties list.
     */
    fun getCustomProperty(key: String): QUserProperty? =
        customProperties.find { it.key == key }
}
