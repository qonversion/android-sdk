package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.SkuDetails
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QProduct(
    @Json(name = "id") val qonversionID: String,
    @Json(name = "store_id") val storeID: String?,
    @Json(name = "type") val type: QProductType,
    @Json(name = "duration") val duration: QProductDuration?
) {
    @Transient
    var skuDetail: SkuDetails? = null
        set(value) {
            prettyPrice = value?.price
            trialDuration = configureTrialPeriod(value?.freeTrialPeriod)
            field = value
        }

    @Transient
    var prettyPrice: String? = null

    @Transient
    var trialDuration: QTrialDuration? = null

    private fun configureTrialPeriod(trialPeriod: String?): QTrialDuration {
        if (trialPeriod.isNullOrEmpty()) {
            return QTrialDuration.NotAvailable
        }

        var period: QTrialDuration = QTrialDuration.Other

        when (trialPeriod) {
            "P3D"-> period = QTrialDuration.ThreeDays
            "P1W"-> period = QTrialDuration.Week
            "P2W"-> period = QTrialDuration.TwoWeeks
            "P4W2D"-> period = QTrialDuration.Month
            "P8W4D"-> period = QTrialDuration.TwoMonths
            "P12W6D"-> period = QTrialDuration.ThreeMonths
            "P25W5D"-> period = QTrialDuration.SixMonths
            "P52W1D"-> period = QTrialDuration.Year
            else-> QTrialDuration.Other
        }

        return period
    }
}