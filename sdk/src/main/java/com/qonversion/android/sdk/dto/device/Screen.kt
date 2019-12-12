package com.qonversion.android.sdk.dto.device

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Screen(
    @Json(name = "height") val height: String,
    @Json(name = "width") val width: String
)