package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.dto.QPurchaseModel
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.QPurchaseUpdateModel
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct

internal open class PurchaseModelInternal(
    val productId: String,
    val oldProductId: String?,
    val updatePolicy: QPurchaseUpdatePolicy?,
    val options: QPurchaseOptions?
) {
    constructor(purchaseModel: QPurchaseModel) : this(
        purchaseModel.productId,
        null,
        null,
        QPurchaseOptions(offerId = purchaseModel.offerId, applyOffer = purchaseModel.applyOffer)
    )

    constructor(product: QProduct, options: QPurchaseOptions? = null) : this(
        product.qonversionID,
        options?.oldProduct?.qonversionID,
        options?.updatePolicy,
        options
    )

    constructor(purchaseModel: QPurchaseUpdateModel) : this(
        purchaseModel.productId,
        purchaseModel.oldProductId,
        purchaseModel.updatePolicy,
        QPurchaseOptions(offerId = purchaseModel.offerId, applyOffer = purchaseModel.applyOffer)
    )

    fun enrich(product: QProduct, oldProduct: QProduct?) = PurchaseModelInternalEnriched(
        productId, product, oldProduct, updatePolicy, options
    )
}
