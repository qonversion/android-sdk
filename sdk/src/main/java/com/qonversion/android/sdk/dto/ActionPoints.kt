package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.dto.automations.ActionPointScreen
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ActionPoints(
    @Json(name = "items") val items: List<Data<ActionPointScreen>>
)
