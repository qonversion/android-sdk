package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.dto.experiments.QExperiment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QRemoteConfig internal constructor(
    @Json(name = "Payload") val payload: Map<String, Any>,
    @Json(name = "Experiment") val experiment: QExperiment?
)
