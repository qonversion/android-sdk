package com.qonversion.android.sdk.internal

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.*
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.billing.BillingError
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.billing.productId
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.mockPrivateField
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
internal class QProductCenterManagerTest {
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockDeviceStorage = mockk<PurchasesCache>(relaxed = true)
    private val mockHandledPurchasesCache = mockk<QHandledPurchasesCache>(relaxed = true)
    private val mockLaunchResultCacheWrapper = mockk<LaunchResultCacheWrapper>(relaxed = true)
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QRepository>(relaxed = true)
    private val mockUserInfoService = mockk<QUserInfoService>(relaxed = true)
    private val mockIdentityManager = mockk<QIdentityManager>(relaxed = true)
    private val mockBillingService = mockk<QonversionBillingService>()
    private val mockConfig = mockk<InternalConfig>(relaxed = true)
    private val mockAppStateProvider = mockk<AppStateProvider>(relaxed = true)
    private val mockRemoteConfigManager = mockk<QRemoteConfigManager>(relaxed = true)

    private lateinit var productCenterManager: QProductCenterManager

    private val productId = "productId"
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
            mockAppStateProvider,
            mockRemoteConfigManager
        )
        productCenterManager.billingService = mockBillingService
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
            mockBillingService.queryPurchases(captureLambda(), any())
        } answers {
            lambda<(BillingError) -> Unit>().captured.invoke(
                BillingError(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, "")
            )
        }

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.queryPurchases(any(), any())
        }

        verify { mockRepository wasNot Called }
        verify(exactly = 0) { mockBillingService.consumePurchases(any()) }
    }

    @Test
    fun `handle pending purchases when launching is finished and query purchases completed`() {
        val spykProductCenterManager = spyk(productCenterManager, recordPrivateCalls = true)
        spykProductCenterManager.mockPrivateField("processingPurchaseOptions", emptyMap<String, QPurchaseOptions>())

        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, false)
        val purchases = listOf(purchase)
        every {
            mockBillingService.queryPurchases(any(), captureLambda())
        } answers {
            lambda<(List<Purchase>) -> Unit>().captured.invoke(
                purchases
            )
        }

        val installDateSlot = slot<Long>()
        val callbackSlot = slot<QonversionLaunchCallback>()
        val entityPurchaseSlot = slot<com.qonversion.android.sdk.internal.purchase.Purchase>()
        every {
            mockRepository.purchase(
                capture(installDateSlot),
                capture(entityPurchaseSlot),
                null,
                capture(callbackSlot)
            )
        } just Runs

        every { mockBillingService.consumePurchases(any()) } just Runs

        spykProductCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.queryPurchases(any(), any())
            mockBillingService.consumePurchases(purchases)
        }

        assertAll(
            "Repository purchase() method was called with invalid arguments",
            { Assert.assertEquals("Wrong purchaseToken value", purchaseToken, entityPurchaseSlot.captured.purchaseToken) },
            { Assert.assertEquals("Wrong installDate value", installDate.milliSecondsToSeconds(), installDateSlot.captured) }
        )
    }

    private fun mockPurchase(
        @Purchase.PurchaseState purchaseState: Int,
        isAcknowledged: Boolean
    ): Purchase {

        val purchase = mockk<Purchase>(relaxed = true)

        every { purchase.products } returns listOf(productId)
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