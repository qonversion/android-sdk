package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.*
import com.qonversion.android.sdk.internal.converter.GoogleBillingPeriodConverter
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
    @Suppress("DEPRECATION")
    var skuDetail: SkuDetails? = null
        set(value) {
            prettyPrice = value?.price
            trialDuration = GoogleBillingPeriodConverter.convertTrialPeriod(value?.freeTrialPeriod)
            field = value
        }

    @Transient
    var offeringID: String? = null

    @Transient
    var prettyPrice: String? = null

    @Transient
    var trialDuration: QTrialDuration = QTrialDuration.Unknown
}
