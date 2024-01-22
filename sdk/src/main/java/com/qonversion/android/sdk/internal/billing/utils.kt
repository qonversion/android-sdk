package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductOfferDetails
import com.qonversion.android.sdk.dto.products.QSubscriptionPeriod
import com.qonversion.android.sdk.dto.products.QProductPricingPhase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val BillingResult.isOk get() = responseCode == BillingClient.BillingResponseCode.OK

internal fun BillingResult.getDescription() =
    "It is a proxy of the Google BillingClient error: ${responseCode.getDescription()}"

internal fun PurchaseHistoryRecord.getDescription() =
    "ProductId: ${this.productId}; PurchaseTime: ${this.purchaseTime.convertLongToTime()}; PurchaseToken: ${this.purchaseToken}"

internal fun Purchase.getDescription() =
    "ProductId: ${this.productId}; OrderId: ${this.orderId}; PurchaseToken: ${this.purchaseToken}"

internal val Purchase.productId: String?
    get() = products.firstOrNull()

internal val PurchaseHistoryRecord.productId: String?
    get() = products.firstOrNull()

internal fun getCurrentTimeInMillis(): Long = Calendar.getInstance().timeInMillis

private const val MAX_BILLING_PHASES_DURATION_YEARS = 55

// Calculates total price for a client if he would use this concrete offer.
// 55 years is the maximum length of all the offer phases
// (3 years max trial and 52 years max recurrent discount payments).
internal val QProductOfferDetails.pricePerMaxDuration: Double get() {
    var totalDays = QSubscriptionPeriod.Unit.Year.inDays * MAX_BILLING_PHASES_DURATION_YEARS
    var totalPrice = .0

    for (pricingPhase in pricingPhases) {
        // Base plan is the last phase, so we just calculate the price of the remaining time
        // of base plan usage.
        if (pricingPhase.isBasePlan) {
            val remainingPeriodCount = if (pricingPhase.billingPeriod.durationDays != 0) {
                totalDays.toDouble() / pricingPhase.billingPeriod.durationDays
            } else {
                Double.MAX_VALUE
            }
            totalPrice += pricingPhase.price.priceAmountMicros * remainingPeriodCount
            break
        }

        // For any trial or intro offer we decrease the amount of days left by its duration
        totalDays -= pricingPhase.durationDays

        // And also add the price for that offer for its total duration.
        if (!pricingPhase.isTrial) {
            totalPrice += pricingPhase.price.priceAmountMicros * pricingPhase.billingCycleCount
        }
    }

    return totalPrice
}

internal val QProductPricingPhase.durationDays get() = when (type) {
    QProductPricingPhase.Type.FreeTrial,
    QProductPricingPhase.Type.DiscountedRecurringPayment,
    QProductPricingPhase.Type.DiscountedSinglePayment ->
        billingPeriod.durationDays * billingCycleCount
    else -> 0
}

internal val QSubscriptionPeriod.durationDays get() = unit.inDays * unitCount

internal val QSubscriptionPeriod.Unit.inDays get() = when (this) {
    QSubscriptionPeriod.Unit.Day -> 1
    QSubscriptionPeriod.Unit.Week -> 7
    QSubscriptionPeriod.Unit.Month -> 30
    QSubscriptionPeriod.Unit.Year -> 365
    QSubscriptionPeriod.Unit.Unknown -> 0
}

@Suppress("DEPRECATION")
internal val QProduct.hasAnyStoreDetails get() = skuDetail != null || storeDetails != null

private fun Long.convertLongToTime(): String {
    val date = Date(this)
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    return format.format(date)
}

private fun @receiver:BillingClient.BillingResponseCode Int.getDescription(): String {
    return when (this) {
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR"
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
