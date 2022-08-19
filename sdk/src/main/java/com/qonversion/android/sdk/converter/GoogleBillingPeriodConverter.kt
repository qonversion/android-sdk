package com.qonversion.android.sdk.converter

import com.qonversion.android.sdk.dto.products.QTrialDuration

object GoogleBillingPeriodConverter {

    private val multipliers = mapOf(
        "Y" to 365,
        "M" to 30,
        "W" to 7,
        "D" to 1
    )

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

    fun convertPeriodToDays(period: String?): Int? {
        if (period.isNullOrEmpty()) {
            return null
        }

        var totalCount = 0
        val regex = Regex("\\d+[a-zA-Z]")
        val results = regex.findAll(period, 0)
        results.forEach { result ->
            val value = result.groupValues.first()
            val digits = value.filter { it.isDigit() }.toInt()
            val letter = value.filter { it.isLetter() }

            val multiplier = multipliers[letter]
            multiplier?.let {
                totalCount += it * digits
            }
        }

        return totalCount
    }
}
