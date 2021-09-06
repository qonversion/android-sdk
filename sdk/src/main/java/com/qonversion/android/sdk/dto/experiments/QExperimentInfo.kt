package com.qonversion.android.sdk.dto.experiments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QExperimentInfo(
    @Json(name = "uid") val experimentID: String,
    @Json(name = "attached") internal var attached: Boolean = false
)
