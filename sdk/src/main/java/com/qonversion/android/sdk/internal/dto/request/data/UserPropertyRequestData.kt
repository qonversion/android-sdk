package com.qonversion.android.sdk.internal.dto.request.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class UserPropertyRequestData(
    @Json(name = "key") val key: String,
    @Json(name = "value") val value: String
)
