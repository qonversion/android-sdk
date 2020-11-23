package com.qonversion.android.sdk.converter

import android.util.Pair
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.extractor.Extractor

class GooglePurchaseConverter(
    private val extractor: Extractor<String>
) : PurchaseConverter<Pair<SkuDetails, com.android.billingclient.api.Purchase>> {
    companion object {
        private const val priceMicrosDivider: Double = 1000000.0
    }

    private val multipliers = mapOf<String, Int>(
        "Y" to 365,
        "M" to 30,
        "W" to 7,
        "D" to 1
    )

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
            price = formatPrice(details.priceAmountMicros),
            priceAmountMicros = details.priceAmountMicros,
            periodUnit = 0,
            periodUnitsCount = getUnitsCountFromPeriod(details.subscriptionPeriod),
            freeTrialPeriod = details.freeTrialPeriod ?: "",
            introductoryAvailable = details.introductoryPrice.isNotEmpty(),
            introductoryPriceAmountMicros = details.introductoryPriceAmountMicros,
            introductoryPrice = getIntroductoryPrice(details),
            introductoryPriceCycles = getIntroductoryPriceCycles(details),
            introductoryPeriodUnit = 0,
            introductoryPeriodUnitsCount = getUnitsCountFromPeriod(details.freeTrialPeriod ?: details.introductoryPricePeriod),
            orderId = purchase.orderId,
            originalOrderId = formatOriginalTransactionId(purchase.orderId),
            packageName = purchase.packageName,
            purchaseTime = purchase.purchaseTime.milliSecondsToSeconds(),
            purchaseState = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            acknowledged = purchase.isAcknowledged,
            autoRenewing = purchase.isAutoRenewing,
            paymentMode = getPaymentMode(details)
        )
    }

    private fun getIntroductoryPriceCycles(details: SkuDetails): Int {
        return if (details.freeTrialPeriod.isNullOrEmpty()) details.introductoryPriceCycles else 0
    }

    private fun getIntroductoryPrice(details: SkuDetails): String {
        if (details.freeTrialPeriod.isNullOrEmpty()) {
            return formatPrice(details.introductoryPriceAmountMicros)
        }

        return "0.0"
    }

    private fun getPaymentMode(details: SkuDetails): Int {
        return if (details.freeTrialPeriod.isNotEmpty()) 2 else 0
    }

    private fun formatPrice(price: Long): String {
        val divideResult = price.toDouble() / (priceMicrosDivider)
        val result = String.format("%.2f", divideResult)

        return result
    }

    private fun formatOriginalTransactionId(transactionId: String): String {
        val regex = Regex("\\.{2}.*")
        val result = regex.replace(transactionId, "")

        return result
    }

    private fun getUnitsCountFromPeriod(period: String?): Int? {
        if (period.isNullOrEmpty()) {
            return null
        }

        var totalCount = 0
        val regex = Regex("\\d+[a-zA-Z]")
        val results = regex.findAll(period, 0)
        results.forEach { result ->
            val value = result.groupValues.first()
            val digits = value.filter { it -> it.isDigit() }.toInt()
            val letter = value.filter { it -> it.isLetter() }

            val multiplier = multipliers[letter]
            multiplier?.let {
                totalCount += it * digits
            }
        }

        return totalCount
    }

}