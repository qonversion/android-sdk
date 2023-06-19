package com.qonversion.android.sdk.internal.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class AttachUserRequest(
    @Json(name = "group_id") val groupId: String
)