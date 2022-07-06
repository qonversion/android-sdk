package com.qonversion.android.sdk.dto.identity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdentityResult(
    @Json(name = "user_id") val userID: String
)
