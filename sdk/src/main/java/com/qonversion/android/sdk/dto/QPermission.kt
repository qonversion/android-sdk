package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class QPermission(
    @Json(name = "id") val permissionID: String,
    @Json(name = "associated_product") val associatedProductID: String,
    @Json(name = "active") val active: Int,
    @Json(name = "renew_state") val renewState: QProductRenewState,
    @Json(name = "started_timestamp") val startedDate: Date,
    @Json(name = "expiration_timestamp") val expirationDate: Date?
)