package com.qonversion.android.sdk.dto.automation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ActionPointsRequest (
    @Json(name = "type") val type: String,
    @Json(name = "active") val active: Int
)