package com.qonversion.android.sdk.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateIdentityRequest(
    @Json(name = "anon_id") val anonID: String,
    @Json(name = "identity_id") val identityID: String
)