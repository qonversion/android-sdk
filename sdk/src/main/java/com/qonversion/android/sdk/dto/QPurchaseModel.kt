package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductStoreDetails

/**
 * Used to provide all the necessary purchase data to the [Qonversion.purchase] method.
 * Can be created manually or using the [QProduct.toPurchaseModel] method.
 *
 * If [offerId] is not specified, then the default offer will be applied - we will choose
 * the cheapest offer for the client.
 * To know how we choose the default offer, see [QProductStoreDetails.defaultSubscriptionOfferDetails].
 *
 * To prevent applying any offer to the purchase (use only bare base plan),
 * call the [QPurchaseModel.removeOffer] method.
 */
data class QPurchaseModel(
    val qonversionProductId: String,
    var offerId: String? = null
) {
    internal var applyOffer = true

    fun removeOffer(): QPurchaseModel = apply {
        applyOffer = false
    }
}
