package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct

internal class PurchaseModelInternalEnriched(
    productId: String,
    val product: QProduct,
    val oldProduct: QProduct?,
    updatePolicy: QPurchaseUpdatePolicy?,
    options: QPurchaseOptions?
) : PurchaseModelInternal(productId, oldProduct?.qonversionID, updatePolicy, options) {

    constructor(
        purchaseModel: PurchaseModelInternal,
        product: QProduct
    ) : this(
        purchaseModel.productId,
        product,
        purchaseModel.options?.oldProduct,
        purchaseModel.updatePolicy,
        purchaseModel.options
    )
}
