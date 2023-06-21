package com.qonversion.android.sdk.dto.experiments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperiment(
    @Json(name = "uid") val identifier: String,
    @Json(name = "name") val name: String,
    @Json(name = "group") val group: QExperimentGroup
)
