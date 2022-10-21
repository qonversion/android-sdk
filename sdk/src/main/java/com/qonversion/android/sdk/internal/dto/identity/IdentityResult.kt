package com.qonversion.android.sdk.internal.dto.identity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class IdentityResult(
    @Json(name = "anon_id") val userID: String
)
