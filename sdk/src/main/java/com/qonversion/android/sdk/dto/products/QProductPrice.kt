package com.qonversion.android.sdk.dto.products

data class QProductPrice(
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val formattedPrice: String,
) {
    val isFree: Boolean = priceAmountMicros == 0L
}
