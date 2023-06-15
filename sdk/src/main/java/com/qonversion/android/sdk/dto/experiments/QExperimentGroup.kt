package com.qonversion.android.sdk.dto.experiments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperimentGroup(
    @Json(name = "Uid") val identifier: String,
    @Json(name = "Name") val name: String,
    @Json(name = "Type") val type: QExperimentGroupType
)
