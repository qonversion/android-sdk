package com.qonversion.android.sdk.old.dto.identity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdentityResult(
    @Json(name = "anon_id") val userID: String
)
