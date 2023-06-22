package com.qonversion.android.sdk.dto.experiments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperimentGroup(
    @Json(name = "uid") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: QExperimentGroupType
)
