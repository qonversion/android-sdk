package com.qonversion.android.sdk

import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.entity.Purchase

class GooglePurchaseConverter : PurchaseConverter<android.util.Pair<SkuDetails, com.android.billingclient.api.Purchase>> {

    override fun convert(purchaseInfo: android.util.Pair<SkuDetails, com.android.billingclient.api.Purchase>): Purchase {
        val details = purchaseInfo.first
        val purchase = purchaseInfo.second
        return Purchase(
            detailsToken = details.originalJson ?: "",
            title = details.title ?: "",
            description = details.description ?: "",
            productId = purchase.sku,
            type = details.type ?: "",
            price = details.introductoryPrice ?: "",
            priceAmountMicros = details.priceAmountMicros,
            currencyCode = details.priceCurrencyCode ?: "",
            subscriptionPeriod = details.subscriptionPeriod ?: "",
            freeTrialPeriod = details.freeTrialPeriod ?: "",
            introductoryPriceAmountMicros = details.introductoryPriceAmountMicros,
            introductoryPricePeriod = details.introductoryPricePeriod ?: "",
            introductoryPrice = details.introductoryPrice ?: "",
            introductoryPriceCycles = details.introductoryPriceCycles ?: "",
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