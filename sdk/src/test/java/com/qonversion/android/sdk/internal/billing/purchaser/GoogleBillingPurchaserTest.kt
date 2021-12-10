package com.qonversion.android.sdk.internal.billing.purchaser

import android.app.Activity
import android.text.TextUtils
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.Exception


internal class GoogleBillingPurchaserTest {

    private lateinit var purchaser: GoogleBillingPurchaser
    private val billingClient = mockk<BillingClient>()
    private val activity = mockk<Activity>()
    private val skuDetails = mockk<SkuDetails>(relaxed = true)
    private val billingResult = mockk<BillingResult>()
    private val updateInfo = mockk<UpdatePurchaseInfo>()

    @Before
    fun setUp() {
        purchaser = GoogleBillingPurchaserImpl(billingClient)

        every { billingClient.launchBillingFlow(activity, any()) } returns billingResult
        every { billingResult.responseCode } returns BillingClient.BillingResponseCode.OK

        every { updateInfo.purchaseToken } returns "test"
        every { updateInfo.prorationMode } returns 0

        mockkStatic(TextUtils::class)
        val strSlot = slot<String>()
        every { TextUtils.isEmpty(capture(strSlot)) } answers { strSlot.captured.isEmpty() }
    }

    @Test
    fun `making new purchase`() {
        // given

        // when and then
        assertDoesNotThrow {
            purchaser.purchase(activity, skuDetails)
        }
    }

    @Test
    fun `making new purchase fails`() {
        // given
        every { billingResult.responseCode } returns BillingClient.BillingResponseCode.ERROR

        // when
        val exception = try {
            purchaser.purchase(activity, skuDetails)
        } catch (e: Exception) {
            e
        }

        // then
        assertThat(exception).isInstanceOf(QonversionException::class.java)
        assertThat((exception as QonversionException).code).isEqualTo(ErrorCode.Purchasing)
    }

    @Test
    fun `updating purchase`() {
        // given

        // when
        assertDoesNotThrow {
            purchaser.purchase(activity, skuDetails, updateInfo)
        }

        // then
        // no other way to check that update info was really used in purchase
        // as it is passed to Params which has no suitable public getters.
        verify(atLeast = 1) {
            updateInfo.purchaseToken
        }
        verify(atLeast = 1) {
            updateInfo.prorationMode
        }
    }

    @Test
    fun `updating purchase fails`() {
        // given
        every { billingResult.responseCode } returns BillingClient.BillingResponseCode.ERROR

        // when
        val exception = try {
            purchaser.purchase(activity, skuDetails, updateInfo)
        } catch (e: Exception) {
            e
        }

        // then
        assertThat(exception).isInstanceOf(QonversionException::class.java)
        assertThat((exception as QonversionException).code).isEqualTo(ErrorCode.Purchasing)
    }
}