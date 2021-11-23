package com.qonversion.android.sdk.old.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Data<T>(
    @Json(name = "data") val data: T
)
