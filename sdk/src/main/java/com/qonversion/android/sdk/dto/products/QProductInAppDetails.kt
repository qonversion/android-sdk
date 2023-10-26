package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.ProductDetails.OneTimePurchaseOfferDetails

data class QProductInAppDetails(
    val originalOneTimePurchaseOfferDetails: OneTimePurchaseOfferDetails
) {
    /**
     * The price of an in-app product.
     */
    val price: QProductPrice = originalOneTimePurchaseOfferDetails.let {
        QProductPrice(it.priceAmountMicros, it.priceCurrencyCode, it.formattedPrice)
    }
}
