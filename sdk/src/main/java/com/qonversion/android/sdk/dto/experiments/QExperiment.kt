package com.qonversion.android.sdk.dto.experiments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperiment(
    @Json(name = "Uid") val identifier: String,
    @Json(name = "Name") val name: String,
    @Json(name = "Group") val group: QExperimentGroup
)
