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
}
