package com.qonversion.android.sdk.old.dto.experiments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperimentGroup(
    @Json(name = "type") val experimentID: String,
    @Json(name = "group") val type: QExperimentGroupType
)
