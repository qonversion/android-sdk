package com.qonversion.android.sdk.internal.billing.utils

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.internal.utils.toTimeString
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ExtensionsTest {

    @Nested
    inner class BillingResultIsOkTest {

        private lateinit var billingResult: BillingResult;

        @BeforeEach
        fun setUp() {
            billingResult = spyk(BillingResult())
        }

        @Test
        fun `billing result is ok`() {
            // given
            every { billingResult.responseCode } returns BillingClient.BillingResponseCode.OK

            // when
            val res = billingResult.isOk

            // then
            assertThat(res).isTrue
        }

        @Test
        fun `billing result is not ok`() {
            // given
            every { billingResult.responseCode } returns BillingClient.BillingResponseCode.ERROR

            // when
            val res = billingResult.isOk

            // then
            assertThat(res).isFalse
        }
    }

    @Nested
    inner class PurchaseSkuTest {

        private lateinit var purchase: Purchase

        @BeforeEach
        fun setUp() {
            purchase = spyk(Purchase("{}", ""))
        }

        @Test
        fun `single sku`() {
            // given
            val sku = "test"
            every { purchase.skus } returns arrayListOf(sku)

            // when
            val res = purchase.sku

            // then
            assertThat(res).isEqualTo(sku)
        }

        @Test
        fun `several skus`() {
            // given
            val sku1 = "test1"
            val sku2 = "test2"
            every { purchase.skus } returns arrayListOf(sku1, sku2)

            // when
            val res = purchase.sku

            // then
            assertThat(res).isEqualTo(sku1)
        }

        @Test
        fun `no sku`() {
            // given
            every { purchase.skus } returns arrayListOf<String>()

            // when
            val res = purchase.sku

            // then
            assertThat(res).isNull()
        }
    }

    @Nested
    inner class PurchaseHistoryRecordSkuTest {

        private lateinit var record: PurchaseHistoryRecord

        @BeforeEach
        fun setUp() {
            record = spyk(PurchaseHistoryRecord("{}", ""))
        }

        @Test
        fun `single sku`() {
            // given
            val sku = "test"
            every { record.skus } returns arrayListOf(sku)

            // when
            val res = record.sku

            // then
            assertThat(res).isEqualTo(sku)
        }

        @Test
        fun `several skus`() {
            // given
            val sku1 = "test1"
            val sku2 = "test2"
            every { record.skus } returns arrayListOf(sku1, sku2)

            // when
            val res = record.sku

            // then
            assertThat(res).isEqualTo(sku1)
        }

        @Test
        fun `no sku`() {
            // given
            every { record.skus } returns arrayListOf<String>()

            // when
            val res = record.sku

            // then
            assertThat(res).isNull()
        }
    }

    @Nested
    inner class GetDescriptionTest {

        @Test
        fun `billing result description`() {
            // given
            val billingResult = spyk(BillingResult())
            every { billingResult.responseCode } returns BillingClient.BillingResponseCode.ERROR

            // when
            val res = billingResult.getDescription()

            // then
            assertThat(res).isEqualTo("It is a proxy of the Google BillingClient error: ERROR")
        }

        @Test
        fun `purchase description`() {
            // given
            val purchase = spyk(Purchase("{}", ""))
            every { purchase.skus } returns arrayListOf("test id")
            every { purchase.orderId } returns "test_order_id"
            every { purchase.purchaseToken } returns "test_token"

            // when
            val res = purchase.getDescription()

            // then
            assertThat(res).isEqualTo("ProductId: test id; OrderId: test_order_id; PurchaseToken: test_token")
        }

        @Test
        fun `purchase history record description`() {
            // given
            val purchaseTime = 42L
            mockkStatic("com.qonversion.android.sdk.internal.utils.DateUtilsKt")
            every { purchaseTime.toTimeString() } returns "purchase_time"

            val purchase = spyk(PurchaseHistoryRecord("{}", ""))
            every { purchase.skus } returns arrayListOf("test id")
            every { purchase.purchaseTime } returns purchaseTime
            every { purchase.purchaseToken } returns "test_token"

            // when
            val res = purchase.getDescription()

            // then
            assertThat(res).isEqualTo("ProductId: test id; PurchaseTime: purchase_time; PurchaseToken: test_token")

            unmockkStatic("com.qonversion.android.sdk.internal.utils.DateUtilsKt")
        }
        
        @Test
        fun `billing response code description`() {
            // given
            val expectedCodes = mapOf(
                BillingClient.BillingResponseCode.SERVICE_TIMEOUT to "SERVICE_TIMEOUT",
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED to "FEATURE_NOT_SUPPORTED",
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED to "SERVICE_DISCONNECTED",
                BillingClient.BillingResponseCode.OK to "OK",
                BillingClient.BillingResponseCode.USER_CANCELED to "USER_CANCELED",
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE to "SERVICE_UNAVAILABLE",
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE to "BILLING_UNAVAILABLE",
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE to "ITEM_UNAVAILABLE",
                BillingClient.BillingResponseCode.DEVELOPER_ERROR to "DEVELOPER_ERROR",
                BillingClient.BillingResponseCode.ERROR to "ERROR",
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED to "ITEM_ALREADY_OWNED",
                BillingClient.BillingResponseCode.ITEM_NOT_OWNED to "ITEM_NOT_OWNED",
                24334253 to "24334253"
            )

            expectedCodes.forEach { (code, expectedResult) ->
                // when
                val res = code.getDescription()

                // then
                assertThat(res).isEqualTo(expectedResult)
            }
        }
    }

    @Nested
    inner class SetSubscriptionUpdateParamsTest {

        private lateinit var builder: BillingFlowParams.Builder
        private lateinit var mockFlowBuilder: BillingFlowParams.SubscriptionUpdateParams.Builder
        private val purchaseToken = "test token"
        private val mockUpdateParams = mockk<BillingFlowParams.SubscriptionUpdateParams>()

        @BeforeEach
        fun setUp() {
            builder = spyk(BillingFlowParams.newBuilder())
            mockFlowBuilder = mockk()

            mockkStatic(BillingFlowParams.SubscriptionUpdateParams::class)
            every { BillingFlowParams.SubscriptionUpdateParams.newBuilder() } returns mockFlowBuilder

            every { mockFlowBuilder.setOldSkuPurchaseToken(any()) } returns mockFlowBuilder
            every { mockFlowBuilder.setReplaceSkusProrationMode(any()) } returns mockFlowBuilder
            every { mockFlowBuilder.build() } returns mockUpdateParams
            every { builder.setSubscriptionUpdateParams(any()) } returns builder
        }

        @AfterEach
        fun tearDown() {
            unmockkStatic(BillingFlowParams.SubscriptionUpdateParams::class)
        }

        @Test
        fun `complete info with proration mode`() {
            // given
            val prorationMode = BillingFlowParams.ProrationMode.DEFERRED
            val updatePurchaseInfo = UpdatePurchaseInfo(purchaseToken, prorationMode)

            // when
            val res = builder.setSubscriptionUpdateParams(updatePurchaseInfo)

            // then
            verifyOrder {
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                mockFlowBuilder.setOldSkuPurchaseToken(purchaseToken)
                mockFlowBuilder.setReplaceSkusProrationMode(prorationMode)
                mockFlowBuilder.build()
                builder.setSubscriptionUpdateParams(mockUpdateParams)
            }
            assertThat(res).isSameAs(builder)
        }

        @Test
        fun `complete info without proration mode`() {
            // given
            val updatePurchaseInfo = UpdatePurchaseInfo(purchaseToken)

            // when
            val res = builder.setSubscriptionUpdateParams(updatePurchaseInfo)

            // then
            verifyOrder {
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                mockFlowBuilder.setOldSkuPurchaseToken(purchaseToken)
                mockFlowBuilder.build()
                builder.setSubscriptionUpdateParams(mockUpdateParams)
            }
            verify(exactly = 0) {
                mockFlowBuilder.setReplaceSkusProrationMode(any())
            }
            assertThat(res).isSameAs(builder)
        }

        @Test
        fun `null info`() {
            // given

            // when
            val res = builder.setSubscriptionUpdateParams()

            // then
            verify(exactly = 0) {
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                mockFlowBuilder.setReplaceSkusProrationMode(any())
            }
            assertThat(res).isSameAs(builder)
        }
    }
}