package com.qonversion.android.sdk.dto.products

/**
 * Information about product's price.
 */
data class QProductPrice(
    /**
     * Total amount of money in micro-units,
     * where 1,000,000 micro-units equal one unit of the currency.
     */
    val priceAmountMicros: Long,

    /**
     * ISO 4217 currency code for price.
     */
    val priceCurrencyCode: String,

    /**
     * Formatted price for the payment, including its currency sign.
     */
    val formattedPrice: String,
) {
    /**
     * True, if the price is zero. False otherwise.
     */
    val isFree: Boolean = priceAmountMicros == 0L
}
