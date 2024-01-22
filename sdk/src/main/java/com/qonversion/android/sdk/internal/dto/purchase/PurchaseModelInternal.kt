package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.dto.QPurchaseModel
import com.qonversion.android.sdk.dto.QPurchaseUpdateModel
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct

internal open class PurchaseModelInternal(
    val productId: String,
    val offerId: String?,
    val applyOffer: Boolean,
    val oldProductId: String?,
    val updatePolicy: QPurchaseUpdatePolicy?,
) {
    constructor(purchaseModel: QPurchaseModel) : this(
        purchaseModel.productId,
        purchaseModel.offerId,
        purchaseModel.applyOffer,
        null,
        null,
    )

    constructor(purchaseModel: QPurchaseUpdateModel) : this(
        purchaseModel.productId,
        purchaseModel.offerId,
        purchaseModel.applyOffer,
        purchaseModel.oldProductId,
        purchaseModel.updatePolicy,
    )

    fun enrich(product: QProduct, oldProduct: QProduct?) = PurchaseModelInternalEnriched(
        productId, product, offerId, applyOffer, oldProductId, oldProduct, updatePolicy
    )
}
