package com.qonversion.android.sdk.entity

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Purchase(
    val detailsToken: String,
    val title: String,
    val description: String,
    val productId: String,
    val type: String,
    val originalPrice: String,
    val originalPriceAmountMicros: Long,
    val priceCurrencyCode: String,
    val price: String,
    val priceAmountMicros: Long,
    val periodUnit: Int?,
    val periodUnitsCount: Int?,
    val freeTrialPeriod: String,
    val introductoryAvailable: Boolean,
    val introductoryPriceAmountMicros: Long,
    val introductoryPrice: String,
    val introductoryPriceCycles: Int,
    val introductoryPeriodUnit: Int?,
    val introductoryPeriodUnitsCount: Int?,
    val orderId: String,
    val originalOrderId: String,
    val packageName: String,
    val purchaseTime: Long,
    val purchaseState: Int,
    val purchaseToken: String,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
    val paymentMode: Int
)
