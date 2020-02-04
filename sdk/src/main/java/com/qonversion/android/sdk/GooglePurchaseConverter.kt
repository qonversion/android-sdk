package com.qonversion.android.sdk

import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.entity.Purchase
import org.json.JSONObject

class GooglePurchaseConverter :
    PurchaseConverter<android.util.Pair<SkuDetails, com.android.billingclient.api.Purchase>> {

    override fun convert(purchaseInfo: android.util.Pair<SkuDetails, com.android.billingclient.api.Purchase>): Purchase {
        val details = purchaseInfo.first
        val purchase = purchaseInfo.second
        return Purchase(
            detailsToken = extractDetailsToken(details.originalJson),
            title = details.title ?: "",
            description = details.description ?: "",
            productId = purchase.sku,
            type = details.type ?: "",
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

    private fun extractDetailsToken(json: String?): String {

        if (json.isNullOrEmpty()) {
            return ""
        }

        val jsonObj = JSONObject(json)

        if (jsonObj.has("skuDetailsToken")) {
            return jsonObj.getString("skuDetailsToken")
        }

        return ""
    }
}