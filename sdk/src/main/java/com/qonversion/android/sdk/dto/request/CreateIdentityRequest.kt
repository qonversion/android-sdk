package com.qonversion.android.sdk.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateIdentityRequest(
    @Json(name = "userId") val userId: String
)