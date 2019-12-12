package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.qonversion.android.sdk.dto.purchase.Inapp

@JsonClass(generateAdapter = true)
data class PurchaseRequest(
    @Json(name = "d") val d: Environment,
    @Json(name = "v") val v: String,
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "inapp") val inapp: Inapp,
    @Json(name = "client_uid") val clientUid: String?
)