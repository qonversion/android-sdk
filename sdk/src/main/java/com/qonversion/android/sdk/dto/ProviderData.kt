package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProviderData(
    @Json(name = "d") val data: Map<String, Any>,
    @Json(name = "provider") val provider: String
)