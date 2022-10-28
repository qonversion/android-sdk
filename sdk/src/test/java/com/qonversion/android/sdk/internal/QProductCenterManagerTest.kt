package com.qonversion.android.sdk.internal

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.listeners.QonversionLaunchCallbackInternal
import com.qonversion.android.sdk.internal.billing.BillingError
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.billing.sku
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.mockPrivateField
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import io.mockk.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertAll
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class QProductCenterManagerTest {
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockDeviceStorage = mockk<PurchasesCache>(relaxed = true)
    private val mockHandledPurchasesCache = mockk<QHandledPurchasesCache>(relaxed = true)
    private val mockLaunchResultCacheWrapper = mockk<LaunchResultCacheWrapper>(relaxed = true)
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockUserInfoService = mockk<QUserInfoService>(relaxed = true)
    private val mockIdentityManager = mockk<QIdentityManager>(relaxed = true)
    private val mockBillingService = mockk<QonversionBillingService>()
    private val mockConsumer = mockk<Consumer>(relaxed = true)
    private val mockConfig = mockk<InternalConfig>(relaxed = true)
    private val mockAppStateProvider = mockk<AppStateProvider>(relaxed = true)

    private lateinit var productCenterManager: QProductCenterManager

    private val fieldSkuDetails = "skuDetails"

    private val skuTypeInApp = BillingClient.SkuType.INAPP
    private val sku = "sku"
    private val purchaseToken = "purchaseToken"
    private val installDate: Long = 1605608753

    @Before
    fun setUp() {
        clearAllMocks()

        mockInstallDate()
        every { mockHandledPurchasesCache.shouldHandlePurchase(any()) } returns true

        productCenterManager = QProductCenterManager(
            mockContext,
            mockRepository,
            mockLogger,
            mockDeviceStorage,
            mockHandledPurchasesCache,
            mockLaunchResultCacheWrapper,
            mockUserInfoService,
            mockIdentityManager,
            mockConfig,
            mockAppStateProvider
        )
        productCenterManager.billingService = mockBillingService
        productCenterManager.consumer = mockConsumer
        mockLaunchResult()
    }

    private fun mockLaunchResult() {
        val launchResult = QLaunchResult("uid", Date(), offerings = null)
        every { mockLaunchResultCacheWrapper.sessionLaunchResult } returns launchResult
    }

    @Test
    fun `handle pending purchases when launching is not finished`() {
        every { mockLaunchResultCacheWrapper.sessionLaunchResult } returns null

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

        verify {
            listOf(
                mockConsumer,
                mockRepository
            ) wasNot Called
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
        val callbackSlot = slot<QonversionLaunchCallbackInternal>()
        val entityPurchaseSlot = slot<com.qonversion.android.sdk.internal.purchase.Purchase>()
        every {
            mockRepository.purchase(
                capture(installDateSlot),
                capture(entityPurchaseSlot),
                null,
                null,
                capture(callbackSlot)
            )
        } just Runs

        every { mockBillingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.queryPurchases(any(), any())
            mockConsumer.consumePurchases(purchases, skuDetails)
        }

        assertAll(
            "Repository purchase() method was called with invalid arguments",
            { Assert.assertEquals("Wrong sku value", sku, entityPurchaseSlot.captured.productId) },
            { Assert.assertEquals("Wrong purchaseToken value", purchaseToken, entityPurchaseSlot.captured.purchaseToken) },
            { Assert.assertEquals("Wrong type value", skuTypeInApp, entityPurchaseSlot.captured.type) },
            { Assert.assertEquals("Wrong installDate value", installDate.milliSecondsToSeconds(), installDateSlot.captured) }
        )
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
            mockManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        } returns mockInfo
    }
}