package com.qonversion.android.sdk

import com.android.billingclient.api.Purchase
import com.android.billingclient.api.*
import com.qonversion.android.sdk.Constants.DEFAULT_SUBS_PERIOD
import com.qonversion.android.sdk.Constants.DEFAULT_SUBS_SKU
import com.qonversion.android.sdk.Constants.INAPP_PURCHASE
import com.qonversion.android.sdk.Constants.INAPP_SKU_DETAILS
import com.qonversion.android.sdk.Constants.SUBS_PURCHASE_INCOMPLETE
import com.qonversion.android.sdk.Constants.PURCHASE_SIGNATURE
import com.qonversion.android.sdk.Constants.SUBS_SKU_DETAILS_INCOMPLETE
import java.lang.reflect.Modifier

fun Any.mockPrivateField(fieldName: String, field: Any?) {
    javaClass.declaredFields
        .filter { it.modifiers.and(Modifier.PRIVATE) > 0 || it.modifiers.and(Modifier.PROTECTED) > 0 }
        .firstOrNull { it.name == fieldName }
        ?.also { it.isAccessible = true }
        ?.set(this, field)
}

fun <T> Any.getPrivateField(name: String): T {
    val field = this::class.java.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as T
}

@Suppress("DEPRECATION")
fun mockSubsSkuDetails(
    sku: String = DEFAULT_SUBS_SKU,
    subsPeriod: String = DEFAULT_SUBS_PERIOD,
    freeTrialPeriod: String? = null
): SkuDetails {
    val skuDetailsJson = mockSubsSkuDetailsJson(sku, subsPeriod, freeTrialPeriod)
    return SkuDetails(skuDetailsJson)
}

fun mockSubsSkuDetailsJson(
    sku: String = DEFAULT_SUBS_SKU,
    subsPeriod: String = DEFAULT_SUBS_PERIOD,
    freeTrialPeriod: String? = null
): String {
    val sb = StringBuilder()
    sb.append("$SUBS_SKU_DETAILS_INCOMPLETE, \"productId\":\"$sku\", \"subscriptionPeriod\":$subsPeriod")
    freeTrialPeriod?.let {
        sb.append(",\"freeTrialPeriod\":\"$it\"")
    }

    return "{${sb}}"
}

fun mockSubsPurchase(sku: String = DEFAULT_SUBS_SKU): Purchase {
    return Purchase("{$SUBS_PURCHASE_INCOMPLETE, \"productId\":\"$sku\"}", PURCHASE_SIGNATURE)
}

fun mockIncorrectSubsPurchase(): Purchase {
    return Purchase("{$SUBS_PURCHASE_INCOMPLETE}", PURCHASE_SIGNATURE)
}

@Suppress("DEPRECATION")
fun mockInAppSkuDetails(): SkuDetails {
    return SkuDetails(mockInAppSkuDetailsJson())
}

fun mockInAppSkuDetailsJson(
): String {
    return "{$INAPP_SKU_DETAILS}"
}

fun mockInAppPurchase(): Purchase {
    return Purchase("{$INAPP_PURCHASE}", PURCHASE_SIGNATURE)
}

object Constants {
    // Subscription purchase without productId
    const val SUBS_PURCHASE_INCOMPLETE = "\"orderId\":\"GPA.0000-0000-0000-0000\"," +
            "\"packageName\":\"com.qonversion.sample\"," +
            "\"purchaseTime\":1631867965714," +
            "\"purchaseState\":1," +
            "\"purchaseToken\":\"XXXXXXX\"," +
            "\"quantity\":1," +
            "\"autoRenewing\":true," +
            "\"acknowledged\":true"

    // Subscription SkuDetails without productId, subscriptionPeriod, freeTrialPeriod
    const val SUBS_SKU_DETAILS_INCOMPLETE = "\"type\":\"subs\"," +
            "\"title\":\"Qonversion Subs\"," +
            "\"name\":\"Qonversion Subscription Weekly\"," +
            "\"price\":\"RUB 439.00\"," +
            "\"price_amount_micros\":439000000," +
            "\"price_currency_code\":\"RUB\"," +
            "\"description\":\"Weekly\"," +
            "\"introductoryPriceAmountMicros\":85000000," +
            "\"introductoryPricePeriod\":\"P3D\"," +
            "\"introductoryPrice\":\"RUB 85.00\"," +
            "\"introductoryPriceCycles\":1," +
            "\"skuDetailsToken\":\"XXXXXXX\""

    const val INAPP_SKU_DETAILS = "\"productId\":\"qonversion_inapp_consumable\"," +
            "\"type\":\"inapp\"," +
            "\"title\":\"Qonversion In-app\"," +
            "\"name\":\"Qonversion In-app Consumable\"," +
            "\"price\":\"RUB 75.00\"," +
            "\"price_amount_micros\":75000000," +
            "\"price_currency_code\":\"RUB\"," +
            "\"description\":\"Consumable\"," +
            "\"skuDetailsToken\":\"XXXXXXX\"}"

    const val INAPP_PURCHASE = "\"orderId\":\"GPA.0000-0000-0000-0000\"," +
            "\"packageName\":\"com.qonversion.sample\"," +
            "\"productId\":\"qonversion_inapp_consumable\"," +
            "\"purchaseTime\":1632238801527," +
            "\"purchaseState\":1," +
            "\"purchaseToken\":\"XXXXXXX\"," +
            "\"quantity\":1," +
            "\"acknowledged\":true"

    const val PURCHASE_SIGNATURE = "mockSignature"
    const val DEFAULT_SUBS_PERIOD = "P1W"
    const val DEFAULT_SUBS_SKU = "subs_weekly"
}