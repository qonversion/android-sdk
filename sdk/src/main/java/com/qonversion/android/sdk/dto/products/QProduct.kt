package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.SkuDetails
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QProduct(
    @Json(name = "id") val qonversionID: String,
    @Json(name = "store_id") val storeID: String?
) {
    @Transient
    var skuDetail: SkuDetails? = null
        set(value) {
            prettyPrice = value?.price
            duration = parseSubscriptionPeriod(value?.subscriptionPeriod)
            trialDuration = parseTrialPeriod(value?.freeTrialPeriod)
            type = when {
                value?.type == "inapp" -> QProductType.InApp
                trialDuration != QTrialDuration.NotAvailable -> QProductType.Trial
                else -> QProductType.Subscription
            }
            field = value
        }

    @Transient
    var type: QProductType = QProductType.Subscription

    @Transient
    var duration: QProductDuration? = null

    @Transient
    var offeringID: String? = null

    @Transient
    var prettyPrice: String? = null

    @Transient
    var trialDuration: QTrialDuration? = null

    private fun parseTrialPeriod(trialPeriod: String?): QTrialDuration {
        if (trialPeriod.isNullOrEmpty()) {
            return QTrialDuration.NotAvailable
        }

        return when (trialPeriod) {
            "P3D" -> QTrialDuration.ThreeDays
            "P7D", "P1W" -> QTrialDuration.Week
            "P14D", "P2W" -> QTrialDuration.TwoWeeks
            "P30D", "P1M", "P4W2D" -> QTrialDuration.Month
            "P60D", "P2M", "P8W4D" -> QTrialDuration.TwoMonths
            "P90D", "P3M", "P12W6D" -> QTrialDuration.ThreeMonths
            "P180D", "P6M", "P25W5D" -> QTrialDuration.SixMonths
            "P365D", "P1Y", "P12M", "P52W1D" -> QTrialDuration.Year
            else -> QTrialDuration.Other
        }
    }

    private fun parseSubscriptionPeriod(subscriptionPeriod: String?): QProductDuration? {
        if (subscriptionPeriod.isNullOrEmpty()) {
            return null
        }

        return when (subscriptionPeriod) {
            "P7D", "P1W" -> QProductDuration.Weekly
            "P30D", "P1M", "P4W2D" -> QProductDuration.Monthly
            "P90D", "P3M", "P12W6D" -> QProductDuration.ThreeMonthly
            "P180D", "P6M", "P25W5D" -> QProductDuration.SixMonthly
            "P365D", "P1Y", "P12M", "P52W1D" -> QProductDuration.Annual
            else -> null
        }
    }
}
