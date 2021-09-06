package com.qonversion.android.sdk.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EventRequest(
    @Json(name = "user") val userId: String,
    @Json(name = "event") val eventName: String,
    @Json(name = "payload") val payload: Map<String, Any>
)
