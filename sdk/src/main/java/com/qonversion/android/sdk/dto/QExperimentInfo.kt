package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperimentInfo (
    @Json(name = "id") val experimentID: String,
    @Json(name = "group") val group: QExperimentGroup
)