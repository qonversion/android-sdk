package com.qonversion.android.sdk.internal.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class IdentityRequest(
    @Json(name = "anon_id") val anonID: String,
    @Json(name = "identity_id") val identityID: String
)
