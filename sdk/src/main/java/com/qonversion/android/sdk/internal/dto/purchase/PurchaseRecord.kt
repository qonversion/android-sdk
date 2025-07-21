package com.qonversion.android.sdk.internal.dto.purchase

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.internal.billing.productId
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class PurchaseRecord(
    @Json(name = "product") val productId: String,
    @Json(name = "purchase_token") val purchaseToken: String,
    @Json(name = "purchase_time") val purchaseTime: Long
) {
    constructor(purchase: Purchase) : this(
        purchase.productId ?: "",
        purchase.purchaseToken,
        purchase.purchaseTime.milliSecondsToSeconds(),
    )
}
