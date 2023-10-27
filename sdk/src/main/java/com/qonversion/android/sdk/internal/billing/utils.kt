package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.dto.products.QProductOfferDetails
import com.qonversion.android.sdk.dto.products.QProductPeriod
import com.qonversion.android.sdk.dto.products.QProductPricingPhase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val BillingResult.isOk get() = responseCode == BillingClient.BillingResponseCode.OK

internal fun BillingResult.getDescription() =
    "It is a proxy of the Google BillingClient error: ${responseCode.getDescription()}"

internal fun PurchaseHistoryRecord.getDescription() =
    "ProductId: ${this.sku}; PurchaseTime: ${this.purchaseTime.convertLongToTime()}; PurchaseToken: ${this.purchaseToken}"

internal fun Purchase.getDescription() =
    "ProductId: ${this.sku}; OrderId: ${this.orderId}; PurchaseToken: ${this.purchaseToken}"

@Suppress("DEPRECATION")
internal val Purchase.sku: String?
    get() = skus.firstOrNull()

@Suppress("DEPRECATION")
internal val PurchaseHistoryRecord.sku: String?
    get() = skus.firstOrNull()

internal fun getCurrentTimeInMillis(): Long = Calendar.getInstance().timeInMillis

private const val MaxBillingPhasesDurationYears = 55

// Calculates total price for a client if he would use this concrete offer.
// 55 years is the maximum length of all the offer phases
// (3 years max trial and 52 years max recurrent discount payments).
internal val QProductOfferDetails.pricePerMaxDuration: Double get() {
    var totalDays = QProductPeriod.Unit.Year.inDays * MaxBillingPhasesDurationYears
    var totalPrice = .0

    for (pricingPhase in pricingPhases) {
        // Base plan is the last phase, so we just calculate the price of the remaining time
        // of base plan usage.
        if (pricingPhase.isBasePlan) {
            val remainingPeriodCount = totalDays / pricingPhase.billingPeriod.durationDays
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
    QProductPricingPhase.Type.SinglePayment ->
        billingPeriod.durationDays * billingCycleCount
    else -> 0
}

internal val QProductPeriod.durationDays get() = unit.inDays * count

internal val QProductPeriod.Unit.inDays get() = when (this) {
    QProductPeriod.Unit.Day -> 1
    QProductPeriod.Unit.Week -> 7
    QProductPeriod.Unit.Month -> 30
    QProductPeriod.Unit.Year -> 365
    QProductPeriod.Unit.Unknown -> 0
}

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
