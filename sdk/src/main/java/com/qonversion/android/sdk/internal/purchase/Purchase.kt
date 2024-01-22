package com.qonversion.android.sdk.internal.purchase

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Purchase(
    val storeProductId: String?,
    val orderId: String,
    val originalOrderId: String,
    val purchaseTime: Long,
    val purchaseToken: String,
)
