package com.qonversion.android.sdk.internal.dto.app

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class App(
    @Json(name = "name") val name: String,
    @Json(name = "version") val version: String,
    @Json(name = "build") val build: String,
    @Json(name = "bundle") val bundle: String
)