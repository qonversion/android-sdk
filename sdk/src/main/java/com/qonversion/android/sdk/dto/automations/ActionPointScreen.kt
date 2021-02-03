package com.qonversion.android.sdk.dto.automations

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ActionPointScreen(
    @Json(name = "screen") val screenId: String
)