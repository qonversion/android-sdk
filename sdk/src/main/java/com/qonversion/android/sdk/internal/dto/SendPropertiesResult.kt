package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.dto.QSavedUserProperty
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class SendPropertiesResult(
    @Json(name = "savedProperties") val savedProperties: List<QSavedUserProperty>,
    @Json(name = "propertyErrors") val propertyErrors: List<PropertyError>,
) {
    @JsonClass(generateAdapter = true)
    internal data class PropertyError(
        @Json(name = "key") val key: String,
        @Json(name = "error") val error: String
    )
}