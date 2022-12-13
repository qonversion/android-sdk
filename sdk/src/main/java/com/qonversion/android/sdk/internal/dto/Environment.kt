package com.qonversion.android.sdk.internal.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
@JsonClass(generateAdapter = true)
internal data class Environment(
    @Json(name = "app_version") val app_version: String,
    @Json(name = "carrier") val carrier: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "locale") val locale: String,
    @Json(name = "manufacturer") val manufacturer: String,
    @Json(name = "model") val model: String,
    @Json(name = "os") val os: String,
    @Json(name = "os_version") val osVersion: String,
    @Json(name = "timezone") val timezone: String,
    @Json(name = "platform") val platform: String,
    @Json(name = "country") val country: String,
    @Json(name = "advertiser_id") val advertiserId: String?
)
