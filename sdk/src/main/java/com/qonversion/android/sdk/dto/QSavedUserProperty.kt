package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QSavedUserProperty(
    @Json(name = "key") val key: String,
    @Json(name = "value") val value: String
)
