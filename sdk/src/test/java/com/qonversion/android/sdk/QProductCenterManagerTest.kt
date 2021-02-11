package com.qonversion.android.sdk

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.billing.BillingError
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PurchasesCache
import com.qonversion.android.sdk.storage.LaunchResultCache
import io.mockk.*
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class QProductCenterManagerTest {
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockDeviceStorage = mockk<PurchasesCache>(relaxed = true)
    private val mockLaunchResultStorage = mockk<LaunchResultCache>(relaxed = true)
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockBillingService: QonversionBillingService = mockk()
    private val mockConsumer = mockk<Consumer>(relaxed = true)

    private lateinit var productCenterManager: QProductCenterManager

    private val fieldLaunchResult = "launchResult"
    private val fieldSkuDetails = "skuDetails"

    private val skuTypeInApp = BillingClient.SkuType.INAPP
    private val skuTypeSubs = BillingClient.SkuType.SUBS
    private val sku = "sku"
    private val purchaseToken = "purchaseToken"
    private val installDate: Long = 1605608753

    @Before
    fun setUp() {
        clearAllMocks()

        mockInstallDate()

        productCenterManager = QProductCenterManager(mockContext, mockRepository, mockLogger, mockDeviceStorage, mockLaunchResultStorage)
        productCenterManager.billingService = mockBillingService
        productCenterManager.consumer = mockConsumer
        mockLaunchResult()
    }

    private fun mockLaunchResult() {
        val launchResult = QLaunchResult("uid", Date(), offerings = null)
        productCenterManager.mockPrivateField(fieldLaunchResult, launchResult)
    }

    @Test
    fun `handle pending purchases when launching is not finished`() {
        productCenterManager.mockPrivateField(fieldLaunchResult, null)

        productCenterManager.onAppForeground()
        verify(exactly = 0) {
            mockBillingService.queryPurchases(any(), any())
        }
    }

    @Test
    fun `handle pending purchases when launching is finished and query purchases failed`() {
        every {
            mockBillingService.queryPurchases(any(), captureLambda())
        } answers {
            lambda<(BillingError) -> Unit>().captured.invoke(
                BillingError(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, "")
            )
        }

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.queryPurchases(any(), any())
        }

        verify(exactly = 0) {
            mockConsumer.consumePurchases(any(), any())
            mockRepository.purchase(any(), any(), any())
        }
    }

    @Test
    fun `handle pending purchases when launching is finished and query purchases completed`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, false)
        val purchases = listOf(purchase)
        val skuDetails = mockSkuDetailsField(skuTypeInApp)
        every {
            mockBillingService.queryPurchases(captureLambda(), any())
        } answers {
            lambda<(List<Purchase>) -> Unit>().captured.invoke(
                purchases
            )
        }

        val installDateSlot = slot<Long>()
        val callbackSlot = slot<QonversionLaunchCallback>()
        val entityPurchaseSlot = slot<com.qonversion.android.sdk.entity.Purchase>()
        every {
            mockRepository.purchase(
                capture(installDateSlot),
                capture(entityPurchaseSlot),
                capture(callbackSlot)
            )
        } just Runs

        every { mockBillingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.queryPurchases(any(), any())
            mockConsumer.consumePurchases(purchases, skuDetails)
        }

        Assertions.assertThat(entityPurchaseSlot.captured.productId).isEqualTo(sku)
        Assertions.assertThat(entityPurchaseSlot.captured.purchaseToken).isEqualTo(purchaseToken)
        Assertions.assertThat(entityPurchaseSlot.captured.type).isEqualTo(skuTypeInApp)

        Assertions.assertThat(installDateSlot.captured).isEqualTo(installDate.milliSecondsToSeconds())
    }

    private fun mockSkuDetailsField(@BillingClient.SkuType skuType: String): Map<String, SkuDetails> {
        val skuDetails = mockSkuDetails(skuType)
        val mapSkuDetails = mutableMapOf<String, SkuDetails>()
        mapSkuDetails[sku] = skuDetails
        productCenterManager.mockPrivateField(fieldSkuDetails, mapSkuDetails)

        return mapSkuDetails
    }

    private fun mockSkuDetails(
        @BillingClient.SkuType skuType: String
    ): SkuDetails {

        return mockk<SkuDetails>(relaxed = true).also {
            every { it.sku } returns sku
            every { it.type } returns skuType
        }
    }

    private fun mockPurchase(
        @Purchase.PurchaseState purchaseState: Int,
        isAcknowledged: Boolean
    ): Purchase {

        val purchase = mockk<Purchase>(relaxed = true)

        every { purchase.sku } returns sku
        every { purchase.purchaseToken } returns purchaseToken
        every { purchase.purchaseState } returns purchaseState
        every { purchase.isAcknowledged } returns isAcknowledged

        return purchase
    }

    private fun mockInstallDate() {
        val packageName = "packageName"

        val mockManager = mockk<PackageManager>()
        val mockInfo = mockk<PackageInfo>()

        every {
            mockContext.packageName
        } returns packageName

        every {
            mockContext.packageManager
        } returns mockManager

        mockInfo.firstInstallTime = installDate

        every {
            mockManager.getPackageInfo(packageName, 0)
        } returns mockInfo
    }
}