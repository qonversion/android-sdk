package com.qonversion.android.sdk.internal.dto.purchase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Inapp(
    @Json(name = "purchase") val purchase: PurchaseDetails,
)

@JsonClass(generateAdapter = true)
internal data class PurchaseDetails(
    @Json(name = "purchase_token") val purchaseToken: String,
    @Json(name = "purchase_time") val purchaseTime: Long,
    @Json(name = "transaction_id") val transactionId: String,
    @Json(name = "original_transaction_id") val originalTransactionId: String,
    @Json(name = "product") val storeProductId: String,
    @Json(name = "product_id") val qProductId: String
)
