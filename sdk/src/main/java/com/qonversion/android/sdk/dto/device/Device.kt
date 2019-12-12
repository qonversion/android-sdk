package com.qonversion.android.sdk.dto.device

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Device(
    @Json(name = "os") val os: Os,
    @Json(name = "screen") val screen: Screen,
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "model") val model: String,
    @Json(name = "carrier") val carrier: String,
    @Json(name = "locale") val locale: String,
    @Json(name = "timezone") val timezone: String,
    @Json(name = "ads") val ads: AdsDto
)