package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QRemoteConfigurationSource(
    @Json(name = "uid") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "assignment_type") val assignmentType: QRemoteConfigurationAssignmentType,
    @Json(name = "type") val type: QRemoteConfigurationSourceType,
    @Json(name = "context_key") val contextKey: String
)
