package com.qonversion.android.sdk.internal.billing.purchaser

import android.app.Activity
import android.text.TextUtils
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.exception.ErrorCode
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.every
import io.mockk.verify
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow


@ExperimentalCoroutinesApi
internal class GoogleBillingPurchaserTest {

    private lateinit var purchaser: GoogleBillingPurchaser
    private val billingClient = mockk<BillingClient>()
    private val activity = mockk<Activity>()
    private val skuDetails = mockk<SkuDetails>(relaxed = true)
    private val billingResult = mockk<BillingResult>()
    private val updateInfo = mockk<UpdatePurchaseInfo>()

    @BeforeEach
    fun setUp() {
        purchaser = GoogleBillingPurchaserImpl(mockk())
        purchaser.setup(billingClient)

        every { billingClient.launchBillingFlow(activity, any()) } returns billingResult
        every { billingResult.responseCode } returns BillingClient.BillingResponseCode.OK

        every { updateInfo.purchaseToken } returns "test"
        every { updateInfo.prorationMode } returns 0

        mockkStatic(TextUtils::class)
        val strSlot = slot<String>()
        every { TextUtils.isEmpty(capture(strSlot)) } answers { strSlot.captured.isEmpty() }

        // Changing main dispatcher for test purposes
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun after() {
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun `making new purchase`() {
        // given

        // when
        assertDoesNotThrow {
            runTest {
                purchaser.purchase(activity, skuDetails)
            }
        }

        // then
        verify { billingClient.launchBillingFlow(activity, any()) }
    }

    @Test
    fun `making new purchase fails`() {
        // given
        every { billingResult.responseCode } returns BillingClient.BillingResponseCode.ERROR

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.Purchasing) {
            purchaser.purchase(activity, skuDetails)
        }

        // then
        verify { billingClient.launchBillingFlow(activity, any()) }
    }

    @Test
    fun `updating purchase`() {
        // given

        // when
        assertDoesNotThrow {
            runTest {
                purchaser.purchase(activity, skuDetails, updateInfo)
            }
        }

        // then
        verify { billingClient.launchBillingFlow(activity, any()) }
        // no other way to check that update info was really used in purchase
        // as it is passed to Params which has no suitable public getters.
        verify { updateInfo.purchaseToken }
        verify { updateInfo.prorationMode }
    }

    @Test
    fun `updating purchase without passing proration mode`() {
        // given
        every { updateInfo.prorationMode } returns null

        // when
        assertDoesNotThrow {
            runTest {
                purchaser.purchase(activity, skuDetails, updateInfo)
            }
        }

        // then
        verify { billingClient.launchBillingFlow(activity, any()) }
        // no other way to check that update info was really used in purchase
        // as it is passed to Params which has no suitable public getters.
        verify { updateInfo.purchaseToken }
    }

    @Test
    fun `updating purchase fails`() {
        // given
        every { billingResult.responseCode } returns BillingClient.BillingResponseCode.ERROR

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.Purchasing) {
            purchaser.purchase(activity, skuDetails, updateInfo)
        }

        // then
        verify { billingClient.launchBillingFlow(activity, any()) }
    }
}
