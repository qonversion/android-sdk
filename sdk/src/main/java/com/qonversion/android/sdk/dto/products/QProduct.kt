package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.*
import com.qonversion.android.sdk.internal.converter.GoogleBillingPeriodConverter
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QProduct(
    @Json(name = "id") val qonversionID: String,
    @Json(name = "store_id") val storeID: String?,
    @Json(name = "base_plan_id") val basePlanID: String?,
    @Json(name = "type") val type: QProductType,
    @Json(name = "duration") val duration: QProductDuration?
) {
    @Transient
    @Deprecated("Consider using storeDetails instead") // todo maybe a Q documentation link for basePlanID usage info
    @Suppress("DEPRECATION")
    var skuDetail: SkuDetails? = null
        set(value) {
            prettyPrice = value?.price
            trialDuration = GoogleBillingPeriodConverter.convertTrialPeriod(value?.freeTrialPeriod)
            field = value
        }

    var storeDetails: QProductStoreDetails? = null

    @Transient
    var offeringID: String? = null

    @Transient
    @Deprecated("Consider using storeDetails instead")
    var prettyPrice: String? = null

    @Transient
    @Deprecated("Consider using storeDetails instead")
    var trialDuration: QTrialDuration = QTrialDuration.Unknown

    internal fun setStoreProductDetails(productDetails: ProductDetails) {
        storeDetails = QProductStoreDetails(productDetails, basePlanID)
    }
}
