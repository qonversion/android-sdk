package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.Device
@JsonClass(generateAdapter = true)
data class Environment(
    @Json(name = "internalUserId") val internalUserId: String,
    @Json(name = "app") val app: App,
    @Json(name = "device") val device: Device
)