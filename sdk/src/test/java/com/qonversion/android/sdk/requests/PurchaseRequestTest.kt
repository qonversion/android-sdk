package com.qonversion.android.sdk.requests

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.Util
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.PurchaseRequest
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.device.Device
import com.qonversion.android.sdk.dto.device.Os
import com.qonversion.android.sdk.dto.device.Screen
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
class PurchaseRequestTest {

    private lateinit var adapter: JsonAdapter<PurchaseRequest>
    private val converter =
        GooglePurchaseConverter(SkuDetailsTokenExtractor())


    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(PurchaseRequest::class.java)
    }

    @Test
    fun purchaseRequestInApp() {

        val purchase = converter.convert(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_INAPP_JSON),
                Purchase(Util.CORRECT_PURCHASE_INAPP_JSON, "SKU")
            )
        )

        val json = adapter.toJson(PurchaseRequest(
            d = Environment(
                internalUserID = "internal_user_id",
                app = App(
                    name = "app_name",
                    build = "app_build",
                    bundle = "app_bundle",
                    version = "app_version"
                ),
                device = Device(
                    os = Os(),
                    ads = AdsDto(true, "edfa"),
                    deviceId = "user_device_id",
                    model = "user_device_model",
                    carrier = "user_device_carrier",
                    locale = "user_device_locale",
                    screen = Screen("user_device_height","user_device_width"),
                    timezone = "user_device_timezone"
                )
            ),
            accessToken = "user_access_token",
            clientUid = "user_client_uid",
            v = "version",
            inapp = Inapp(
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
            )
        ))
        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("d"))
        Assert.assertTrue(jsonObj.has("access_token"))
        Assert.assertTrue(jsonObj.has("client_uid"))
        Assert.assertTrue(jsonObj.has("v"))
        Assert.assertTrue(jsonObj.has("inapp"))

        Assert.assertEquals("user_access_token", jsonObj.get("access_token"))
        Assert.assertEquals("user_client_uid", jsonObj.get("client_uid"))
        Assert.assertEquals("version", jsonObj.get("v"))
    }

    @Test
    fun purchaseRequestSub() {

        val purchase = converter.convert(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_SUB_JSON),
                Purchase(Util.CORRECT_PURCHASE_SUB_JSON, "SKU")
            )
        )

        val json = adapter.toJson(PurchaseRequest(
            d = Environment(
                internalUserID = "internal_user_id",
                app = App(
                    name = "app_name",
                    build = "app_build",
                    bundle = "app_bundle",
                    version = "app_version"
                ),
                device = Device(
                    os = Os(),
                    ads = AdsDto(true, "edfa"),
                    deviceId = "user_device_id",
                    model = "user_device_model",
                    carrier = "user_device_carrier",
                    locale = "user_device_locale",
                    screen = Screen("user_device_height","user_device_width"),
                    timezone = "user_device_timezone"
                )
            ),
            accessToken = "user_access_token",
            clientUid = "user_client_uid",
            v = "version",
            inapp = Inapp(
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
            )
        ))
        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("d"))
        Assert.assertTrue(jsonObj.has("access_token"))
        Assert.assertTrue(jsonObj.has("client_uid"))
        Assert.assertTrue(jsonObj.has("v"))
        Assert.assertTrue(jsonObj.has("inapp"))

        Assert.assertEquals("user_access_token", jsonObj.get("access_token"))
        Assert.assertEquals("user_client_uid", jsonObj.get("client_uid"))
        Assert.assertEquals("version", jsonObj.get("v"))
    }
}