package com.qonversion.android.sdk.internal.dto.purchase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class History(
    @Json(name = "product") val product: String,
    @Json(name = "purchase_token") val purchaseToken: String,
    @Json(name = "purchase_time") val purchaseTime: Long,
    @Json(name = "currency") val priceCurrencyCode: String?,
    @Json(name = "value") val price: String?
)
