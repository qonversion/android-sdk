package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.internal.dto.automations.ActionPointScreen
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ActionPoints(
    @Json(name = "items") val items: List<Data<ActionPointScreen>>
)
