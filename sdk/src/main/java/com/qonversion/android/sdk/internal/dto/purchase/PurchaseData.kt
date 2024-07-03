package com.qonversion.android.sdk.internal.dto.purchase

import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class PurchaseData(
    val storeProductId: String?,
    val orderId: String,
    val originalOrderId: String,
    val purchaseTime: Long,
    val purchaseToken: String,
) {
    internal fun toHistory(): History? = storeProductId?.let {
        History(
            storeProductId,
            purchaseToken,
            purchaseTime.milliSecondsToSeconds()
        )
    }

    internal fun toInApp(): Inapp = Inapp(
        toPurchaseDetails()
    )

    internal fun toPurchaseDetails(qProductId: String? = null): PurchaseDetails = PurchaseDetails(
        purchaseToken,
        purchaseTime,
        orderId,
        originalOrderId,
        storeProductId ?: "",
        qProductId ?: ""
    )
}
