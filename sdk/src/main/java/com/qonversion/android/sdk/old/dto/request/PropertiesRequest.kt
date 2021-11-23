package com.qonversion.android.sdk.old.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PropertiesRequest(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "q_uid") val clientUid: String?,
    @Json(name = "properties") val properties: Map<String, String>
)
