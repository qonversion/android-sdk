package com.qonversion.android.sdk.dto.device

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdsDto(
    @Json(name = "trackingEnabled") val trackingEnabled: Boolean
)