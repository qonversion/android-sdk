package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductStoreDetails

/**
 * Used to provide all the necessary purchase data to the [Qonversion.purchase] method.
 * Can be created manually or using the [QProduct.toPurchaseModel] method.
 *
 * Requires Qonversion product identifier - [productId].
 *
 * If [offerId] is not specified, then the default offer will be applied. To know how we choose
 * the default offer, see [QProductStoreDetails.defaultSubscriptionOfferDetails].
 *
 * If you want to remove any intro/trial offer from the purchase (use only a bare base plan),
 * call the [removeOffer] method.
 */
data class QPurchaseModel @JvmOverloads constructor(
    val productId: String,
    var offerId: String? = null
) {
    internal var applyOffer = true

    fun removeOffer(): QPurchaseModel = apply {
        applyOffer = false
    }
}
