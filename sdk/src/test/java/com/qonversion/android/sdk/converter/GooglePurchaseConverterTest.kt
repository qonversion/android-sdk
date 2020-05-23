package com.qonversion.android.sdk.converter

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_PURCHASE_INAPP_JSON
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_PURCHASE_SUB_JSON
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_SKU_DETAILS_INAPP_JSON
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_SKU_DETAILS_SUB_JSON
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GooglePurchaseConverterTest {

    @Test
    fun convertCorrectPurchase() {
        val converted = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            .convert(
                Pair(
                    SkuDetails(CORRECT_SKU_DETAILS_INAPP_JSON),
                    Purchase(CORRECT_PURCHASE_INAPP_JSON, "SKU")
                )
            )

        assertEquals("conversion-test-purchase", converted.description)
        assertEquals("XXXXXXX", converted.detailsToken)
        assertEquals("GPA.0000-0000-0000-00000", converted.orderId)
        assertEquals("RUB 500.00", converted.originalPrice)
        assertEquals(500000000, converted.originalPriceAmountMicros)
        assertEquals("com.qonversion.android.sdk", converted.packageName)
        assertEquals("RUB 500.00", converted.price)
        assertEquals(500000000, converted.priceAmountMicros)
        assertEquals("RUB", converted.priceCurrencyCode)
        assertEquals("conversion_test_purchase", converted.productId)
        assertEquals(1, converted.purchaseState)
        assertEquals(1575404326564, converted.purchaseTime)
        assertEquals("XXXXXXX", converted.purchaseToken)
        assertEquals("conversion-test-purchase (Qonversion)", converted.title)
        assertEquals("inapp", converted.type)
        assertEquals("", converted.freeTrialPeriod)
        assertEquals("", converted.introductoryPrice)
        assertEquals(0, converted.introductoryPriceAmountMicros)
        assertEquals("", converted.introductoryPriceCycles)
        assertEquals("", converted.introductoryPricePeriod)
        assertEquals("", converted.subscriptionPeriod)
        assertFalse(converted.acknowledged)
        assertFalse(converted.autoRenewing)

    }

    @Test
    fun convertSubscription() {
        val converted = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            .convert(
                Pair(
                    SkuDetails(CORRECT_SKU_DETAILS_SUB_JSON),
                    Purchase(CORRECT_PURCHASE_SUB_JSON, "SKU")
                )
            )

        assertEquals("conversion-test-subscribe", converted.description)
        assertEquals("XXXXXXX", converted.detailsToken)
        assertEquals("P3D", converted.freeTrialPeriod)
        assertEquals("RUB 100.00", converted.introductoryPrice)
        assertEquals(100000000, converted.introductoryPriceAmountMicros)
        assertEquals("1", converted.introductoryPriceCycles)
        assertEquals("P1M", converted.introductoryPricePeriod)
        assertEquals("GPA.0000-0000-0000-00000", converted.orderId)
        assertEquals("RUB 200.00", converted.originalPrice)
        assertEquals(200000000, converted.originalPriceAmountMicros)
        assertEquals("com.qonversion.android.sdk", converted.packageName)
        assertEquals("RUB 200.00", converted.price)
        assertEquals(200000000, converted.priceAmountMicros)
        assertEquals("RUB", converted.priceCurrencyCode)
        assertEquals("conversion_test_subscribe", converted.productId)
        assertEquals(1, converted.purchaseState)
        assertEquals(1575404669520, converted.purchaseTime)
        assertEquals("XXXXXXX", converted.purchaseToken)
        assertEquals("P1M", converted.subscriptionPeriod)
        assertEquals("conversion-test-subscribe (Qonversion)", converted.title)
        assertEquals("subs", converted.type)
        assertFalse(converted.acknowledged)
        assertTrue(converted.autoRenewing)
    }
}