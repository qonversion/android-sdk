package com.qonversion.android.sdk.internal.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SendPushTokenRequest(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "q_uid") val clientUid: String?,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "push_token") val pushToken: String
)
