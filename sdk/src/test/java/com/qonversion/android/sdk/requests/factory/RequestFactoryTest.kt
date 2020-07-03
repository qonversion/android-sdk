package com.qonversion.android.sdk.requests.factory

import com.qonversion.android.sdk.RequestFactory
import com.qonversion.android.sdk.dto.AttributionRequest
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.purchasequeue.MockEnvironmentProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestFactoryTest {

    private lateinit var requestFactory: RequestFactory

    @Before
    fun setup() {
        requestFactory = RequestFactory(
            environmentProvider = MockEnvironmentProvider(),
            internalUserId = "test_internal_user_id",
            key = "test_key",
            sdkVersion = "test_sdk_version",
            trackingEnabled = false
        )
    }

    @Test
    fun createInitRequestTest() {
        val initRequest = requestFactory.createInitRequest("test_edfa", "test_client_uid")
        Assert.assertEquals(initRequest.clientUid, "test_client_uid")
        Assert.assertEquals(initRequest.accessToken, "test_key")
        Assert.assertEquals(initRequest.v, "test_sdk_version")
        Assert.assertEquals(initRequest.d.app.build, "test_app_build")
        Assert.assertEquals(initRequest.d.app.bundle, "test_app_bundle")
        Assert.assertEquals(initRequest.d.app.version, "test_app_version")
        Assert.assertEquals(initRequest.d.app.name, "test_app_name")
        Assert.assertEquals(initRequest.d.device.ads.edfa, "test_edfa")
        Assert.assertEquals(initRequest.d.device.ads.trackingEnabled, false)
        Assert.assertEquals(initRequest.d.device.os.name, "test_os_name")
        Assert.assertEquals(initRequest.d.device.os.version, "test_os_version")
        Assert.assertEquals(initRequest.d.device.screen.width, "test_width")
        Assert.assertEquals(initRequest.d.device.screen.height, "test_height")
        Assert.assertEquals(initRequest.d.device.carrier, "test_carrier")
        Assert.assertEquals(initRequest.d.device.deviceId, "test_device_id")
        Assert.assertEquals(initRequest.d.device.locale, "test_locale")
        Assert.assertEquals(initRequest.d.device.model, "test_model")
        Assert.assertEquals(initRequest.d.device.timezone, "test_timezone")
        Assert.assertEquals(initRequest.d.internalUserID, "test_internal_user_id")
    }

    @Test
    fun createPurchaseRequestTest() {
        val purchaseRequest = requestFactory.createPurchaseRequest(purchase = Purchase(
            detailsToken = "test_details_token",
            title = "test_title",
            description = "test_description",
            productId = "test_product_id",
            type = "test_type",
            originalPrice = "test_original_price",
            originalPriceAmountMicros = 111111,
            priceCurrencyCode = "test_price_currency_code",
            price = "test_price",
            priceAmountMicros = 121212,
            subscriptionPeriod = "test_subscription_period",
            freeTrialPeriod = "test_free_trial_period",
            introductoryPriceAmountMicros = 131313,
            introductoryPricePeriod = "test_introductory_price_period",
            introductoryPrice = "test_introductory_price",
            introductoryPriceCycles = "test_introductory_price_cycles",
            orderId = "test_order_id",
            packageName = "test_package_name",
            purchaseTime = 141414,
            purchaseState = 1,
            purchaseToken = "test_purchase_token",
            acknowledged = true,
            autoRenewing = false
        ), uid = "test_client_uid")

        Assert.assertEquals(purchaseRequest.inapp.detailsToken, "test_details_token")
        Assert.assertEquals(purchaseRequest.inapp.title, "test_title")
        Assert.assertEquals(purchaseRequest.inapp.description, "test_description")
        Assert.assertEquals(purchaseRequest.inapp.productId, "test_product_id")
        Assert.assertEquals(purchaseRequest.inapp.type, "test_type")
        Assert.assertEquals(purchaseRequest.inapp.originalPrice, "test_original_price")
        Assert.assertEquals(purchaseRequest.inapp.originalPriceAmountMicros, 111111)
        Assert.assertEquals(purchaseRequest.inapp.priceCurrencyCode, "test_price_currency_code")
        Assert.assertEquals(purchaseRequest.inapp.price, "test_price")
        Assert.assertEquals(purchaseRequest.inapp.priceAmountMicros, 121212)
        Assert.assertEquals(purchaseRequest.inapp.subscriptionPeriod, "test_subscription_period")
        Assert.assertEquals(purchaseRequest.inapp.freeTrialPeriod, "test_free_trial_period")
        Assert.assertEquals(purchaseRequest.inapp.introductoryPriceAmountMicros, 131313)
        Assert.assertEquals(purchaseRequest.inapp.introductoryPricePeriod, "test_introductory_price_period")
        Assert.assertEquals(purchaseRequest.inapp.introductoryPrice, "test_introductory_price")
        Assert.assertEquals(purchaseRequest.inapp.introductoryPriceCycles, "test_introductory_price_cycles")
        Assert.assertEquals(purchaseRequest.inapp.orderId, "test_order_id")
        Assert.assertEquals(purchaseRequest.inapp.packageName, "test_package_name")
        Assert.assertEquals(purchaseRequest.inapp.purchaseTime, 141414)
        Assert.assertEquals(purchaseRequest.inapp.purchaseState, 1)
        Assert.assertEquals(purchaseRequest.inapp.purchaseToken, "test_purchase_token")
        Assert.assertEquals(purchaseRequest.inapp.acknowledged, true)
        Assert.assertEquals(purchaseRequest.inapp.autoRenewing, false)

        Assert.assertEquals(purchaseRequest.clientUid, "test_client_uid")
        Assert.assertEquals(purchaseRequest.accessToken, "test_key")
        Assert.assertEquals(purchaseRequest.v, "test_sdk_version")
        Assert.assertEquals(purchaseRequest.d.app.build, "test_app_build")
        Assert.assertEquals(purchaseRequest.d.app.bundle, "test_app_bundle")
        Assert.assertEquals(purchaseRequest.d.app.version, "test_app_version")
        Assert.assertEquals(purchaseRequest.d.app.name, "test_app_name")
        Assert.assertEquals(purchaseRequest.d.device.ads.edfa, null)
        Assert.assertEquals(purchaseRequest.d.device.ads.trackingEnabled, false)
        Assert.assertEquals(purchaseRequest.d.device.os.name, "test_os_name")
        Assert.assertEquals(purchaseRequest.d.device.os.version, "test_os_version")
        Assert.assertEquals(purchaseRequest.d.device.screen.width, "test_width")
        Assert.assertEquals(purchaseRequest.d.device.screen.height, "test_height")
        Assert.assertEquals(purchaseRequest.d.device.carrier, "test_carrier")
        Assert.assertEquals(purchaseRequest.d.device.deviceId, "test_device_id")
        Assert.assertEquals(purchaseRequest.d.device.locale, "test_locale")
        Assert.assertEquals(purchaseRequest.d.device.model, "test_model")
        Assert.assertEquals(purchaseRequest.d.device.timezone, "test_timezone")
        Assert.assertEquals(purchaseRequest.d.internalUserID, "test_internal_user_id")
    }

    @Test
    fun createAttributionRequestTest() {
        val attributionRequest = requestFactory.createAttributionRequest(
            conversionInfo = mapOf(
                "test_string_key" to "test_string_value",
                "test_int_key" to 1000,
                "test_double_key" to 1000.00,
                "test_boolean_key" to true
            ),
            from = "test_from",
            conversionUid = "test_conversation_uid",
            uid =  "test_client_uid"
        ) as AttributionRequest

        Assert.assertEquals(attributionRequest.providerData.provider, "test_from")
        Assert.assertEquals(attributionRequest.providerData.uid, "test_conversation_uid")
        Assert.assertEquals(attributionRequest.providerData.data["test_string_key"], "test_string_value")
        Assert.assertEquals(attributionRequest.providerData.data["test_int_key"], 1000)
        Assert.assertEquals(attributionRequest.providerData.data["test_double_key"], 1000.00)
        Assert.assertEquals(attributionRequest.providerData.data["test_boolean_key"], true)

        Assert.assertEquals(attributionRequest.clientUid, "test_client_uid")
        Assert.assertEquals(attributionRequest.accessToken, "test_key")
        Assert.assertEquals(attributionRequest.v, "test_sdk_version")
        Assert.assertEquals(attributionRequest.d.app.build, "test_app_build")
        Assert.assertEquals(attributionRequest.d.app.bundle, "test_app_bundle")
        Assert.assertEquals(attributionRequest.d.app.version, "test_app_version")
        Assert.assertEquals(attributionRequest.d.app.name, "test_app_name")
        Assert.assertEquals(attributionRequest.d.device.ads.edfa, null)
        Assert.assertEquals(attributionRequest.d.device.ads.trackingEnabled, false)
        Assert.assertEquals(attributionRequest.d.device.os.name, "test_os_name")
        Assert.assertEquals(attributionRequest.d.device.os.version, "test_os_version")
        Assert.assertEquals(attributionRequest.d.device.screen.width, "test_width")
        Assert.assertEquals(attributionRequest.d.device.screen.height, "test_height")
        Assert.assertEquals(attributionRequest.d.device.carrier, "test_carrier")
        Assert.assertEquals(attributionRequest.d.device.deviceId, "test_device_id")
        Assert.assertEquals(attributionRequest.d.device.locale, "test_locale")
        Assert.assertEquals(attributionRequest.d.device.model, "test_model")
        Assert.assertEquals(attributionRequest.d.device.timezone, "test_timezone")
        Assert.assertEquals(attributionRequest.d.internalUserID, "test_internal_user_id")
    }

}