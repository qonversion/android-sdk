package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct

internal open class PurchaseOptionsInternal(
    val product: QProduct,
    val oldProductId: String?,
    val updatePolicy: QPurchaseUpdatePolicy?,
    val options: QPurchaseOptions?
) {
    constructor(product: QProduct, options: QPurchaseOptions? = null) : this(
        product,
        options?.oldProduct?.qonversionId,
        options?.updatePolicy,
        options
    )

    fun enrich(oldProduct: QProduct?) = PurchaseOptionsInternalEnriched(
        product, oldProduct, updatePolicy, options
    )
}
