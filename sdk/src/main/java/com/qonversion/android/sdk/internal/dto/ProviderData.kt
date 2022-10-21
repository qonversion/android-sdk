package com.qonversion.android.sdk.internal.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ProviderData(
    @Json(name = "d") val data: Map<String, Any>,
    @Json(name = "provider") val provider: String
)
