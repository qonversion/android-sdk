package com.qonversion.android.sdk.old.dto

import com.qonversion.android.sdk.old.dto.automations.ActionPointScreen
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ActionPoints(
    @Json(name = "items") val items: List<Data<ActionPointScreen>>
)
