package com.qonversion.android.sdk.dto.automation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Automation(
    @Json(name = "type") val type: String,
    @Json(name = "id") val id: String
)
