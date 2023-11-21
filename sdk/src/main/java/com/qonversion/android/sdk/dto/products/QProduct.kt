package com.qonversion.android.sdk.dto.products

import com.android.billingclient.api.*
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QPurchaseModel
import com.qonversion.android.sdk.dto.QPurchaseUpdateModel
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
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

    @Transient
    var storeDetails: QProductStoreDetails? = null
        private set

    @Transient
    var offeringID: String? = null

    @Transient
    @Deprecated("Consider using storeDetails instead")
    var prettyPrice: String? = null

    @Transient
    @Deprecated("Consider using storeDetails instead")
    var trialDuration: QTrialDuration = QTrialDuration.Unknown

    /**
     * Converts this product to purchase model to pass to [Qonversion.purchase].
     * @param offerId concrete offer identifier if necessary.
     *                If the products' base plan id is specified, but offer id is not provided for
     *                purchase, then default offer will be used.
     *                Ignored if base plan id is not specified.
     * @return purchase model to pass to the purchase method.
     */
    fun toPurchaseModel(offerId: String? = null): QPurchaseModel {
        return QPurchaseModel(qonversionID, offerId)
    }

    /**
     * Converts this product to purchase update (upgrade/downgrade) model
     * to pass to [Qonversion.updatePurchase].
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade
     *                     will be initialized.
     * @param updatePolicy purchase update policy.
     * @return purchase model to pass to the update purchase method.
     */
    fun toPurchaseUpdateModel(
        oldProductId: String,
        updatePolicy: QPurchaseUpdatePolicy? = null
    ): QPurchaseUpdateModel {
        return QPurchaseUpdateModel(qonversionID, oldProductId, updatePolicy)
    }

    internal fun setStoreProductDetails(productDetails: ProductDetails) {
        storeDetails = QProductStoreDetails(productDetails, basePlanID)
    }
}
