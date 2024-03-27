package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.dto.experiments.QExperiment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QRemoteConfig internal constructor(
    @Json(name = "payload") val payload: Map<String, Any>,
    @Json(name = "experiment") val experiment: QExperiment?,
    @Json(name = "source") internal val sourceApi: QRemoteConfigurationSource?
) {
    val source: QRemoteConfigurationSource get() = sourceApi!!

    internal val isCorrect = sourceApi != null
}
