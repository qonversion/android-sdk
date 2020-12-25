package com.qonversion.android.sdk.dto.automation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ViewsRequest (
    @Json(name = "user") val userID: String
)