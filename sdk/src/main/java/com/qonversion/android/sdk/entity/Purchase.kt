package com.qonversion.android.sdk.entity

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
    val subscriptionPeriod: String,
    val freeTrialPeriod: String,
    val introductoryPriceAmountMicros: Long,
    val introductoryPricePeriod: String,
    val introductoryPrice: String,
    val introductoryPriceCycles: String,
    val orderId: String,
    val packageName: String,
    val purchaseTime: Long,
    val purchaseState: Int,
    val purchaseToken: String,
    val acknowledged: Boolean,
    val autoRenewing: Boolean
)
