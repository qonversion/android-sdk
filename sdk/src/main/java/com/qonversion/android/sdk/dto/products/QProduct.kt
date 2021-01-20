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
            "P7D", "P1W"-> period = QTrialDuration.Week
            "P14D", "P2W"-> period = QTrialDuration.TwoWeeks
            "P30D", "P1M"-> period = QTrialDuration.Month
            "P60D", "P2M"-> period = QTrialDuration.TwoMonths
            "P90D", "P3M"-> period = QTrialDuration.ThreeMonths
            "P180D", "P6M"-> period = QTrialDuration.SixMonths
            "P365D", "P12M", "P1Y"-> period = QTrialDuration.Year
            else-> QTrialDuration.Other
        }

        return period
    }
}