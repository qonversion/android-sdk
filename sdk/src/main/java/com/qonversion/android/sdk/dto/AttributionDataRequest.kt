package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AttributionDataRequest(
    @Json(name = "d") val d: Environment,
    @Json(name = "provider") val provider: String,
    @Json(name = "uid") val advertisingId: String?
)