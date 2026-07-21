package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.OneTimePurchaseOfferDetails

/**
 * This class contains all the information about the Google in-app product details.
 */
data class QProductInAppDetails(
    /**
     * Original [OneTimePurchaseOfferDetails] received from Google Play Billing Library.
     */
    val originalOneTimePurchaseOfferDetails: OneTimePurchaseOfferDetails
) {
    /**
     * The price of the in-app product.
     */
    val price: QProductPrice = originalOneTimePurchaseOfferDetails.let {
        QProductPrice(it.priceAmountMicros, it.priceCurrencyCode, it.formattedPrice)
    }

    /**
     * Identifier of the Google Play purchase option this offer belongs to.
     * Null for one-time products configured without purchase options.
     */
    val purchaseOptionId: String? = originalOneTimePurchaseOfferDetails.purchaseOptionId

    /**
     * The offer token required to pass to [com.android.billingclient.api.BillingFlowParams]
     * to purchase this in-app product. Null for a one-time product without a
     * per-transaction offer token.
     */
    val offerToken: String? = originalOneTimePurchaseOfferDetails.offerToken
}
