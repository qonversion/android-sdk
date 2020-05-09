package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.Device
@JsonClass(generateAdapter = true)
data class ProviderData(
    @Json(name = "d") val data: Map<String, Any>,
    @Json(name = "provide") val provider: String,
    @Json(name = "uid") val uid: String
)