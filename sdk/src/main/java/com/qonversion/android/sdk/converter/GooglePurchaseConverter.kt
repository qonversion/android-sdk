package com.qonversion.android.sdk.converter

import android.util.Pair
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.extractor.Extractor

class GooglePurchaseConverter(
    private val extractor: Extractor<String>
) : PurchaseConverter<Pair<SkuDetails, com.android.billingclient.api.Purchase>> {

    override fun convert(purchaseInfo: android.util.Pair<SkuDetails, com.android.billingclient.api.Purchase>): Purchase {
        val details = purchaseInfo.first
        val purchase = purchaseInfo.second
        return Purchase(
            detailsToken = extractor.extract(details.originalJson),
            title = details.title,
            description = details.description,
            productId = purchase.sku,
            type = details.type,
            originalPrice = details.originalPrice ?: "",
            originalPriceAmountMicros = details.originalPriceAmountMicros,
            priceCurrencyCode = details.priceCurrencyCode,
            price = details.price,
            priceAmountMicros = details.priceAmountMicros,
            subscriptionPeriod = details.subscriptionPeriod ?: "",
            freeTrialPeriod = details.freeTrialPeriod ?: "",
            introductoryPriceAmountMicros = details.introductoryPriceAmountMicros,
            introductoryPricePeriod = details.introductoryPricePeriod ?: "",
            introductoryPrice = details.introductoryPrice ?: "",
            introductoryPriceCycles = details.introductoryPriceCycles.toString(),
            orderId = purchase.orderId,
            packageName = purchase.packageName,
            purchaseTime = purchase.purchaseTime,
            purchaseState = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            acknowledged = purchase.isAcknowledged,
            autoRenewing = purchase.isAutoRenewing
        )
    }
}