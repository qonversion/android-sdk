package com.qonversion.android.sdk.internal.billing.utils

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun BillingResult.getDescription() =
    "It is a proxy of the Google BillingClient error: ${responseCode.getDescription()}"

internal val BillingResult.isOk get() = responseCode == BillingClient.BillingResponseCode.OK

internal fun Purchase.getDescription() =
    "ProductId: ${this.sku}; OrderId: ${this.orderId}; PurchaseToken: ${this.purchaseToken}"

internal val Purchase.sku: String? get() = skus.firstOrNull()

internal fun PurchaseHistoryRecord.getDescription() =
    "ProductId: ${this.sku}; " +
            "PurchaseTime: ${this.purchaseTime.convertLongToTime()}; " +
            "PurchaseToken: ${this.purchaseToken}"

internal val PurchaseHistoryRecord.sku: String? get() = skus.firstOrNull()

private fun Long.convertLongToTime(): String {
    val date = Date(this)
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    return format.format(date)
}

internal fun @receiver:BillingClient.BillingResponseCode Int.getDescription(): String {
    return when (this) {
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "SERVICE_TIMEOUT"
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
        BillingClient.BillingResponseCode.OK -> "OK"
        BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
        BillingClient.BillingResponseCode.ERROR -> "ERROR"
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
        else -> "$this"
    }
}
