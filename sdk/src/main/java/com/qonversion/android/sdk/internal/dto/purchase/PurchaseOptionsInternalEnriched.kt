package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct

internal class PurchaseOptionsInternalEnriched(
    product: QProduct,
    val oldProduct: QProduct?,
    updatePolicy: QPurchaseUpdatePolicy?,
    options: QPurchaseOptions?
) : PurchaseOptionsInternal(product, oldProduct?.qonversionId, updatePolicy, options)
