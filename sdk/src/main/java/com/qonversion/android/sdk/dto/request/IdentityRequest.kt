package com.qonversion.android.sdk.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdentityRequest(
    @Json(name = "anon_id") val anonID: String
)
