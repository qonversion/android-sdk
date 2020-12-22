package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QAutomation(
    @Json(name = "type") val type: String,
    @Json(name = "id") val id: String
)
