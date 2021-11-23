package com.qonversion.android.sdk.old.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdentityRequest(
    @Json(name = "anon_id") val anonID: String,
    @Json(name = "identity_id") val identityID: String
)
