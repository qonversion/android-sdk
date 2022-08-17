package com.qonversion.android.sdk.converter

import com.qonversion.android.sdk.dto.products.QProductDuration
import com.qonversion.android.sdk.dto.products.QTrialDuration

object GoogleBillingPeriodConverter {
    fun convertTrialPeriod(trialPeriod: String?): QTrialDuration {
        if (trialPeriod.isNullOrEmpty()) {
            return QTrialDuration.NotAvailable
        }

        var period: QTrialDuration = QTrialDuration.Other

        when (trialPeriod) {
            "P3D" -> period = QTrialDuration.ThreeDays
            "P7D", "P1W" -> period = QTrialDuration.Week
            "P14D", "P2W" -> period = QTrialDuration.TwoWeeks
            "P30D", "P1M", "P4W2D" -> period = QTrialDuration.Month
            "P60D", "P2M", "P8W4D" -> period = QTrialDuration.TwoMonths
            "P90D", "P3M", "P12W6D" -> period = QTrialDuration.ThreeMonths
            "P180D", "P6M", "P25W5D" -> period = QTrialDuration.SixMonths
            "P365D", "P12M", "P52W1D", "P1Y" -> period = QTrialDuration.Year
            else -> QTrialDuration.Other
        }

        return period
    }

    fun convertSubscriptionPeriod(subscriptionPeriod: String?): QProductDuration? {
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
