package com.qonversion.android.sdk.converter

import android.util.Pair
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.Constants.PRICE_MICROS_DIVIDER
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.billing.sku
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.extractor.Extractor

class GooglePurchaseConverter(
    private val extractor: Extractor<String>
) : PurchaseConverter<Pair<SkuDetails, com.android.billingclient.api.Purchase>> {
    companion object {
        private const val daysPeriodUnit = 0
    }

    private val multipliers = mapOf<String, Int>(
        "Y" to 365,
        "M" to 30,
        "W" to 7,
        "D" to 1
    )

    override fun convertPurchases(
        skuDetails: Map<String, SkuDetails>,
        purchases: List<com.android.billingclient.api.Purchase>
    ): List<Purchase> {
        val pairs = purchases.mapNotNull {
            val skuDetail = skuDetails[it.sku]

            if (skuDetail != null) {
                 Pair.create(skuDetail, it)
            } else {
                null
            }
        }

        val result = convertPurchasesFromList(pairs)

        return result
    }

    override fun convertPurchase(purchaseInfo: Pair<SkuDetails, com.android.billingclient.api.Purchase>): Purchase? {
        val details = purchaseInfo.first
        val purchase = purchaseInfo.second
        val sku = purchase.sku ?: return null

        return Purchase(
                detailsToken = extractor.extract(details.originalJson),
                title = details.title,
                description = details.description,
                productId = sku,
                type = details.type,
                originalPrice = details.originalPrice ?: "",
                originalPriceAmountMicros = details.originalPriceAmountMicros,
                priceCurrencyCode = details.priceCurrencyCode,
                price = formatPrice(details.priceAmountMicros),
                priceAmountMicros = details.priceAmountMicros,
                subscriptionPeriod = details.subscriptionPeriod,
                periodUnit = getUnitsTypeFromPeriod(details.subscriptionPeriod),
                periodUnitsCount = getUnitsCountFromPeriod(details.subscriptionPeriod),
                freeTrialPeriod = details.freeTrialPeriod ?: "",
                introductoryAvailable = details.introductoryPrice.isNotEmpty(),
                introductoryPriceAmountMicros = details.introductoryPriceAmountMicros,
                introductoryPrice = getIntroductoryPrice(details),
                introductoryPriceCycles = getIntroductoryPriceCycles(details),
                introductoryPeriodUnit = daysPeriodUnit,
                introductoryPeriodUnitsCount = getIntroductoryUnitsCountFromPeriod(
                    details.freeTrialPeriod ?: details.introductoryPricePeriod
                ),
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

    private fun convertPurchasesFromList(
        purchaseInfo: List<Pair<SkuDetails, com.android.billingclient.api.Purchase>>
    ): List<Purchase> {
        return purchaseInfo.mapNotNull {
            convertPurchase(it)
        }
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
        val divideResult = price.toDouble() / (PRICE_MICROS_DIVIDER)
        val result = String.format("%.2f", divideResult)

        return result
    }

    private fun formatOriginalTransactionId(transactionId: String): String {
        val regex = Regex("\\.{2}.*")
        val result = regex.replace(transactionId, "")

        return result
    }

    private fun getUnitsTypeFromPeriod(period: String?): Int? {
        if (period.isNullOrEmpty()) {
            return null
        }

        val result = period.last().toString()

        val periodUnit = when (result) {
            "Y" -> 3
            "M" -> 2
            "W" -> 1
            "D" -> 0
            else -> null
        }

        return periodUnit
    }

    private fun getUnitsCountFromPeriod(period: String?): Int? {
        if (period.isNullOrEmpty()) {
            return null
        }

        val unitsCount = period.substring(1..period.length - 2)

        return unitsCount.toInt()
    }

    private fun getIntroductoryUnitsCountFromPeriod(period: String?): Int? {
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
