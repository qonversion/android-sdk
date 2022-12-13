package com.qonversion.android.sdk.internal.dto.automations

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ActionPointScreen(
    @Json(name = "screen") val screenId: String
)
