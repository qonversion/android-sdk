package com.qonversion.android.sdk.internal.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class PropertiesRequest(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "q_uid") val clientUid: String?,
    @Json(name = "properties") val properties: Map<String, String>
)
