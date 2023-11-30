package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductStoreDetails

/**
 * Used to provide all the necessary purchase data to the [Qonversion.updatePurchase] method.
 * Can be created manually or using the [QProduct.toPurchaseUpdateModel] method.
 *
 * If [offerId] is not specified, then the default offer will be applied. To know how we choose
 * the default offer, see [QProductStoreDetails.defaultSubscriptionOfferDetails].
 *
 * To prevent applying any offer to the purchase (use only bare base plan),
 * call the [QPurchaseModel.removeOffer] method.
 *
 * If the [updatePolicy] is not provided, then default one
 * will be selected - [QPurchaseUpdatePolicy.WithTimeProration].
 */
data class QPurchaseUpdateModel(
    val productId: String,
    var oldProductId: String,
    var updatePolicy: QPurchaseUpdatePolicy? = null,
    var offerId: String? = null
) {
    internal var applyOffer = true

    fun removeOffer(): QPurchaseUpdateModel = apply {
        applyOffer = false
    }
}
