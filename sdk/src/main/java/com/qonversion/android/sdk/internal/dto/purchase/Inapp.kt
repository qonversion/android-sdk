package com.qonversion.android.sdk.internal.dto.purchase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Inapp(
    @Json(name = "purchase") val purchase: PurchaseDetails,
    @Json(name = "introductory_offer") val introductoryOffer: IntroductoryOfferDetails?
)

@JsonClass(generateAdapter = true)
internal data class PurchaseDetails(
    @Json(name = "product") val productId: String,
    @Json(name = "purchase_token") val purchaseToken: String,
    @Json(name = "purchase_time") val purchaseTime: Long,
    @Json(name = "currency") val priceCurrencyCode: String,
    @Json(name = "value") val price: String,
    @Json(name = "transaction_id") val transactionId: String,
    @Json(name = "original_transaction_id") val originalTransactionId: String,
    @Json(name = "period_unit") val periodUnit: Int?,
    @Json(name = "period_number_of_units") val periodUnitsCount: Int?,
    @Json(name = "country") val country: String?,
    @Json(name = "product_id") val qProductId: String
)

@JsonClass(generateAdapter = true)
internal data class IntroductoryOfferDetails(
    @Json(name = "value") val price: String,
    @Json(name = "period_unit") val periodUnit: Int,
    @Json(name = "period_number_of_units") val periodUnitsCount: Int,
    @Json(name = "number_of_periods") val periodsCount: Int,
    @Json(name = "payment_mode") val paymentMode: Int
)
