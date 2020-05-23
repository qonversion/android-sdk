package com.qonversion.android.sdk.requests

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.Util
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InAppRequestTest {

    private lateinit var adapter: JsonAdapter<Inapp>
    private val converter =
        GooglePurchaseConverter(SkuDetailsTokenExtractor())

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(Inapp::class.java)
    }

    @Test
    fun inAppRequestInAppWithCorrectData() {

        val purchase = converter.convert(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_INAPP_JSON),
                Purchase(Util.CORRECT_PURCHASE_INAPP_JSON, "SKU")
            )
        )

        val json = adapter.toJson(Inapp(
            detailsToken = purchase.detailsToken,
            title = purchase.title,
            description = purchase.description,
            productId = purchase.productId,
            type = purchase.type,
            originalPrice = purchase.originalPrice,
            originalPriceAmountMicros = purchase.originalPriceAmountMicros,
            priceCurrencyCode = purchase.priceCurrencyCode,
            price = purchase.price,
            priceAmountMicros = purchase.priceAmountMicros,
            subscriptionPeriod = purchase.subscriptionPeriod,
            freeTrialPeriod = purchase.freeTrialPeriod,
            introductoryPriceAmountMicros = purchase.introductoryPriceAmountMicros,
            introductoryPricePeriod = purchase.introductoryPricePeriod,
            introductoryPrice = purchase.introductoryPrice,
            introductoryPriceCycles = purchase.introductoryPriceCycles,
            orderId = purchase.orderId,
            packageName = purchase.packageName,
            purchaseTime = purchase.purchaseTime,
            purchaseState = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            acknowledged = purchase.acknowledged,
            autoRenewing = purchase.autoRenewing
        ))

        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("detailsToken"))
        Assert.assertTrue(jsonObj.has("title"))
        Assert.assertTrue(jsonObj.has("description"))
        Assert.assertTrue(jsonObj.has("productId"))
        Assert.assertTrue(jsonObj.has("type"))
        Assert.assertTrue(jsonObj.has("originalPrice"))
        Assert.assertTrue(jsonObj.has("originalPriceAmountMicros"))
        Assert.assertTrue(jsonObj.has("priceCurrencyCode"))
        Assert.assertTrue(jsonObj.has("price"))
        Assert.assertTrue(jsonObj.has("priceAmountMicros"))
        Assert.assertTrue(jsonObj.has("subscriptionPeriod"))
        Assert.assertTrue(jsonObj.has("freeTrialPeriod"))
        Assert.assertTrue(jsonObj.has("introductoryPriceAmountMicros"))
        Assert.assertTrue(jsonObj.has("introductoryPricePeriod"))
        Assert.assertTrue(jsonObj.has("introductoryPrice"))
        Assert.assertTrue(jsonObj.has("introductoryPriceCycles"))
        Assert.assertTrue(jsonObj.has("orderId"))
        Assert.assertTrue(jsonObj.has("packageName"))
        Assert.assertTrue(jsonObj.has("purchaseTime"))
        Assert.assertTrue(jsonObj.has("purchaseState"))
        Assert.assertTrue(jsonObj.has("purchaseToken"))
        Assert.assertTrue(jsonObj.has("acknowledged"))
        Assert.assertTrue(jsonObj.has("autoRenewing"))

        Assert.assertEquals("XXXXXXX", jsonObj.get("detailsToken"))
        Assert.assertEquals("conversion-test-purchase (Qonversion)", jsonObj.get("title"))
        Assert.assertEquals("conversion-test-purchase", jsonObj.get("description"))
        Assert.assertEquals("conversion_test_purchase", jsonObj.get("productId"))
        Assert.assertEquals("inapp", jsonObj.get("type"))
        Assert.assertEquals("RUB 500.00", jsonObj.get("originalPrice"))
        Assert.assertEquals(500000000, jsonObj.get("originalPriceAmountMicros"))
        Assert.assertEquals("RUB", jsonObj.get("priceCurrencyCode"))
        Assert.assertEquals("RUB 500.00", jsonObj.get("price"))
        Assert.assertEquals(500000000, jsonObj.get("priceAmountMicros"))
        Assert.assertEquals("", jsonObj.get("subscriptionPeriod"))
        Assert.assertEquals("", jsonObj.get("freeTrialPeriod"))
        Assert.assertEquals(0, jsonObj.get("introductoryPriceAmountMicros"))
        Assert.assertEquals("", jsonObj.get("introductoryPricePeriod"))
        Assert.assertEquals("", jsonObj.get("introductoryPrice"))
        Assert.assertEquals("", jsonObj.get("introductoryPriceCycles"))
        Assert.assertEquals("GPA.0000-0000-0000-00000", jsonObj.get("orderId"))
        Assert.assertEquals("com.qonversion.android.sdk", jsonObj.get("packageName"))
        Assert.assertEquals(1575404326564, jsonObj.get("purchaseTime"))
        Assert.assertEquals(1, jsonObj.get("purchaseState"))
        Assert.assertEquals("XXXXXXX", jsonObj.get("purchaseToken"))
        Assert.assertEquals(false, jsonObj.get("acknowledged"))
        Assert.assertEquals(false, jsonObj.get("autoRenewing"))
    }

    @Test
    fun inAppRequestSubWithCorrectData() {

        val purchase = converter.convert(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_SUB_JSON),
                Purchase(Util.CORRECT_PURCHASE_SUB_JSON, "SKU")
            )
        )

        val json = adapter.toJson(Inapp(
            detailsToken = purchase.detailsToken,
            title = purchase.title,
            description = purchase.description,
            productId = purchase.productId,
            type = purchase.type,
            originalPrice = purchase.originalPrice,
            originalPriceAmountMicros = purchase.originalPriceAmountMicros,
            priceCurrencyCode = purchase.priceCurrencyCode,
            price = purchase.price,
            priceAmountMicros = purchase.priceAmountMicros,
            subscriptionPeriod = purchase.subscriptionPeriod,
            freeTrialPeriod = purchase.freeTrialPeriod,
            introductoryPriceAmountMicros = purchase.introductoryPriceAmountMicros,
            introductoryPricePeriod = purchase.introductoryPricePeriod,
            introductoryPrice = purchase.introductoryPrice,
            introductoryPriceCycles = purchase.introductoryPriceCycles,
            orderId = purchase.orderId,
            packageName = purchase.packageName,
            purchaseTime = purchase.purchaseTime,
            purchaseState = purchase.purchaseState,
            purchaseToken = purchase.purchaseToken,
            acknowledged = purchase.acknowledged,
            autoRenewing = purchase.autoRenewing
        ))

        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("detailsToken"))
        Assert.assertTrue(jsonObj.has("title"))
        Assert.assertTrue(jsonObj.has("description"))
        Assert.assertTrue(jsonObj.has("productId"))
        Assert.assertTrue(jsonObj.has("type"))
        Assert.assertTrue(jsonObj.has("originalPrice"))
        Assert.assertTrue(jsonObj.has("originalPriceAmountMicros"))
        Assert.assertTrue(jsonObj.has("priceCurrencyCode"))
        Assert.assertTrue(jsonObj.has("price"))
        Assert.assertTrue(jsonObj.has("priceAmountMicros"))
        Assert.assertTrue(jsonObj.has("subscriptionPeriod"))
        Assert.assertTrue(jsonObj.has("freeTrialPeriod"))
        Assert.assertTrue(jsonObj.has("introductoryPriceAmountMicros"))
        Assert.assertTrue(jsonObj.has("introductoryPricePeriod"))
        Assert.assertTrue(jsonObj.has("introductoryPrice"))
        Assert.assertTrue(jsonObj.has("introductoryPriceCycles"))
        Assert.assertTrue(jsonObj.has("orderId"))
        Assert.assertTrue(jsonObj.has("packageName"))
        Assert.assertTrue(jsonObj.has("purchaseTime"))
        Assert.assertTrue(jsonObj.has("purchaseState"))
        Assert.assertTrue(jsonObj.has("purchaseToken"))
        Assert.assertTrue(jsonObj.has("acknowledged"))
        Assert.assertTrue(jsonObj.has("autoRenewing"))

        Assert.assertEquals("XXXXXXX", jsonObj.get("detailsToken"))
        Assert.assertEquals("conversion-test-subscribe (Qonversion)", jsonObj.get("title"))
        Assert.assertEquals("conversion-test-subscribe", jsonObj.get("description"))
        Assert.assertEquals("conversion_test_subscribe", jsonObj.get("productId"))
        Assert.assertEquals("subs", jsonObj.get("type"))
        Assert.assertEquals("RUB 200.00", jsonObj.get("originalPrice"))
        Assert.assertEquals(200000000, jsonObj.get("originalPriceAmountMicros"))
        Assert.assertEquals("RUB", jsonObj.get("priceCurrencyCode"))
        Assert.assertEquals("RUB 200.00", jsonObj.get("price"))
        Assert.assertEquals(200000000, jsonObj.get("priceAmountMicros"))
        Assert.assertEquals("P1M", jsonObj.get("subscriptionPeriod"))
        Assert.assertEquals("P3D", jsonObj.get("freeTrialPeriod"))
        Assert.assertEquals(100000000, jsonObj.get("introductoryPriceAmountMicros"))
        Assert.assertEquals("P1M", jsonObj.get("introductoryPricePeriod"))
        Assert.assertEquals("RUB 100.00", jsonObj.get("introductoryPrice"))
        Assert.assertEquals("1", jsonObj.get("introductoryPriceCycles"))
        Assert.assertEquals("GPA.0000-0000-0000-00000", jsonObj.get("orderId"))
        Assert.assertEquals("com.qonversion.android.sdk", jsonObj.get("packageName"))
        Assert.assertEquals(1575404669520, jsonObj.get("purchaseTime"))
        Assert.assertEquals(1, jsonObj.get("purchaseState"))
        Assert.assertEquals("XXXXXXX", jsonObj.get("purchaseToken"))
        Assert.assertEquals(false, jsonObj.get("acknowledged"))
        Assert.assertEquals(true, jsonObj.get("autoRenewing"))
    }
}