package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct

internal class PurchaseModelInternalEnriched(
    productId: String,
    val product: QProduct,
    offerId: String?,
    withoutOffer: Boolean,
    oldProductId: String?,
    val oldProduct: QProduct?,
    updatePolicy: QPurchaseUpdatePolicy?,
) : PurchaseModelInternal(productId, offerId, withoutOffer, oldProductId, updatePolicy) {

    constructor(
        purchaseModel: PurchaseModelInternal,
        product: QProduct,
        oldProduct: QProduct?
    ) : this(
        purchaseModel.productId,
        product,
        purchaseModel.offerId,
        purchaseModel.withoutOffer,
        purchaseModel.oldProductId,
        oldProduct,
        purchaseModel.updatePolicy
    )
}
