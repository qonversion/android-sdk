package com.qonversion.android.sdk.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import java.text.SimpleDateFormat
import java.util.*

fun Int.toBoolean() = this != 0

fun String?.toBoolean() = this == "1"

fun Boolean.toInt() = if (this) 1 else 0

fun Boolean.stringValue() = if (this) "1" else "0"

fun BillingResult.getDescription() =
    "It is a proxy of the Google BillingClient error: ${responseCode.getDescription()}"

fun PurchaseHistoryRecord.getDescription() =
    "ProductId: ${this.sku}; PurchaseTime: ${this.purchaseTime.convertLongToTime()}; PurchaseToken: ${this.purchaseToken}"

fun Purchase.getDescription() =
    "ProductId: ${this.sku}; OrderId: ${this.orderId}; PurchaseToken: ${this.purchaseToken}"

val Purchase.sku: String
    get() = skus[0]


val PurchaseHistoryRecord.sku: String
    get() = skus[0]

fun Long.milliSecondsToSeconds(): Long = this / 1000

fun Long.secondsToMilliSeconds(): Long = this * 1000

private fun Long.convertLongToTime(): String {
    val date = Date(this)
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    return format.format(date)
}

private fun @receiver:BillingClient.BillingResponseCode Int.getDescription(): String {
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