package com.qonversion.android.sdk.dto.properties

data class QUserProperties(
    private val properties: List<QUserProperty>
) {
    val definedPropertiesList: List<QUserProperty> = properties
        .filter { it.definedKey !== QUserPropertyKey.Custom }

    val definedPropertiesMap: Map<QUserPropertyKey, String> = definedPropertiesList
        .associate { it.definedKey to it.value }

    val customPropertiesList: List<QUserProperty> = properties
        .filter { it.definedKey === QUserPropertyKey.Custom }

    val customPropertiesMap: Map<String, String> = customPropertiesList
        .associate { it.key to it.value }

    val propertiesList: List<QUserProperty> = properties

    val propertiesMap: Map<String, String> = propertiesList
        .associate { it.key to it.value }

    fun getCustomProperty(key: String): QUserProperty? =
        customPropertiesList.find { it.key == key }

    fun getDefinedProperty(key: QUserPropertyKey): QUserProperty? =
        definedPropertiesList.find { it.definedKey === key }

    fun getProperty(key: String): QUserProperty? =
        propertiesList.find { it.key == key }
}
