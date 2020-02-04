package com.qonversion.android.sdk.dto.purchase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Inapp(
    @Json(name = "detailsToken") val detailsToken: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "productId") val productId: String,
    @Json(name = "type") val type: String,
    @Json(name = "originalPriceAmountMicros") val originalPriceAmountMicros: Long,
    @Json(name = "originalPrice") val originalPrice: String,
    @Json(name = "price") val price: String,
    @Json(name = "priceAmountMicros") val priceAmountMicros: Long,
    @Json(name = "priceCurrencyCode") val priceCurrencyCode: String,
    @Json(name = "subscriptionPeriod") val subscriptionPeriod: String,
    @Json(name = "freeTrialPeriod") val freeTrialPeriod: String,
    @Json(name = "introductoryPriceAmountMicros") val introductoryPriceAmountMicros: Long,
    @Json(name = "introductoryPricePeriod") val introductoryPricePeriod: String,
    @Json(name = "introductoryPrice") val introductoryPrice: String,
    @Json(name = "introductoryPriceCycles") val introductoryPriceCycles: String,
    @Json(name = "orderId") val orderId: String,
    @Json(name = "packageName") val packageName: String,
    @Json(name = "purchaseTime") val purchaseTime: Long,
    @Json(name = "purchaseState") val purchaseState: Int,
    @Json(name = "purchaseToken") val purchaseToken: String,
    @Json(name = "acknowledged") val acknowledged: Boolean,
    @Json(name = "autoRenewing") val autoRenewing: Boolean
)

