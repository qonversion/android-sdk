package com.qonversion.android.sdk.internal.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Response(
    @Json(name = "client_id") val clientId: String?,
    @Json(name = "client_uid") val clientUid: String?,
    @Json(name = "client_target_id") val clientTargetId: String?
)
