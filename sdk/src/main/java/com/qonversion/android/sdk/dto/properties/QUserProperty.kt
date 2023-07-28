package com.qonversion.android.sdk.dto.properties

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QUserProperty(
    @Json(name = "key") val key: String,
    @Json(name = "value") val value: String
) {
    /**
     * [QUserPropertyKey] used to set this property.
     * Returns [QUserPropertyKey.Custom] for custom properties.
     */
    val definedKey: QUserPropertyKey = QUserPropertyKey.fromString(key)
}
