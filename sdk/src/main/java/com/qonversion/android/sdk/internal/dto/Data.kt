package com.qonversion.android.sdk.internal.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Data<T>(
    @Json(name = "data") val data: T
)
