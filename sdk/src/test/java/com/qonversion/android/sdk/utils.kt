package com.qonversion.android.sdk

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.Constants.DEFAULT_SUBS_SKU
import com.qonversion.android.sdk.Constants.INAPP_PURCHASE
import com.qonversion.android.sdk.Constants.SUBS_PURCHASE_INCOMPLETE
import com.qonversion.android.sdk.Constants.PURCHASE_SIGNATURE
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

fun mockSubsPurchase(sku: String = DEFAULT_SUBS_SKU): Purchase {
    return Purchase("{$SUBS_PURCHASE_INCOMPLETE, \"productId\":\"$sku\"}", PURCHASE_SIGNATURE)
}

fun mockIncorrectSubsPurchase(): Purchase {
    return Purchase("{$SUBS_PURCHASE_INCOMPLETE}", PURCHASE_SIGNATURE)
}

fun mockInAppPurchase(): Purchase {
    return Purchase("{$INAPP_PURCHASE}", PURCHASE_SIGNATURE)
}

object Constants {
    // Subscription purchase without productId
    const val SUBS_PURCHASE_INCOMPLETE = "\"orderId\":\"GPA.0000-0000-0000-0000\"," +
            "\"packageName\":\"io.qonversion.sample\"," +
            "\"purchaseTime\":1631867965714," +
            "\"purchaseState\":1," +
            "\"purchaseToken\":\"XXXXXXX\"," +
            "\"quantity\":1," +
            "\"autoRenewing\":true," +
            "\"acknowledged\":true"

    const val INAPP_PURCHASE = "\"orderId\":\"GPA.0000-0000-0000-0000\"," +
            "\"packageName\":\"io.qonversion.sample\"," +
            "\"productId\":\"qonversion_inapp_consumable\"," +
            "\"purchaseTime\":1632238801527," +
            "\"purchaseState\":1," +
            "\"purchaseToken\":\"XXXXXXX\"," +
            "\"quantity\":1," +
            "\"acknowledged\":true"

    const val PURCHASE_SIGNATURE = "mockSignature"
    const val DEFAULT_SUBS_SKU = "subs_weekly"
}