package com.qonversion.android.sdk.internal.dto

internal data class ProductStoreId(
    val productId: String,
    val basePlanId: String?, // absent for inapp products
    val offerId: String? = null
)
