package com.qonversion.android.sdk

import android.app.Application
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.billing.BillingError
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.logger.Logger
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class QProductCenterManagerTest {
    private val purchaseToken = "token"
    private val sku = "sku"
    private val skuTypeInApp = BillingClient.SkuType.INAPP
    private val skuTypeSubs = BillingClient.SkuType.SUBS
    private val qProduct = "qProduct"
    private val uid = "uid"

    private val billingServiceCreator: BillingServiceCreator = mockk()
    private val logger: Logger = mockk(relaxed = true)
    private val context = mockk<Application>(relaxed = true)
    private val repository = mockk<QonversionRepository>(relaxed = true)

    private val isObserveMode = false

    private lateinit var productCenterManager: QProductCenterManager
    private lateinit var purchasesListener: QonversionBillingService.PurchasesListener

    private val billingService: QonversionBillingService = mockk()
    private val fieldIsLaunchingFinished = "isLaunchingFinished"
    private val fieldSkuDetails = "skuDetails"
    private val fieldIsObserveMode = "isObserveMode"

    @Before
    fun setUp() {
        clearAllMocks()

        val purchasesUpdatedSlot = slot<QonversionBillingService.PurchasesListener>()
        every {
            billingServiceCreator.create(capture(purchasesUpdatedSlot))
        } answers {
            purchasesListener = purchasesUpdatedSlot.captured
            billingService
        }

        productCenterManager =
            QProductCenterManager(context, isObserveMode, repository, billingServiceCreator, logger)
    }

    @Test
    fun `handle pending purchases when launching is not finished`() {
        productCenterManager.onAppForeground()
        verify(exactly = 0) {
            billingService.queryPurchases(any(), any())
        }
    }

    @Test
    fun `handle pending purchases when launching is finished and query purchases completed`() {
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)

        every {
            billingService.queryPurchases(any(), any())
        } just Runs

        productCenterManager.onAppForeground()
        verify(exactly = 1) {
            billingService.queryPurchases(any(), any())
        }
    }

    @Test
    fun `handle pending purchases when launching is finished and query purchases failed`() {
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)

        every {
            billingService.queryPurchases(any(), captureLambda())
        } answers {
            lambda<(BillingError) -> Unit>().captured.invoke(
                BillingError(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, "")
            )
        }

        productCenterManager.onAppForeground()

        // Todo add more verifications
        verify(exactly = 0) {
            billingService.consume(any())
            billingService.acknowledge(any())
            repository.purchase(any(), any(), any())
        }
    }

    @Test
    fun `consume purchases when observe mode is true`() {
        mockQueryPurchasesResponse(skuTypeSubs, Purchase.PurchaseState.PURCHASED)
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)
        productCenterManager.mockPrivateField(fieldIsObserveMode, true)

        productCenterManager.onAppForeground()

        verify(exactly = 0) {
            billingService.acknowledge(any())
            billingService.consume(any())
        }
    }

    @Test
    fun `consume purchases with pending state`() {
        mockQueryPurchasesResponse(skuTypeInApp, Purchase.PurchaseState.PENDING)
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)

        productCenterManager.onAppForeground()

        verify(exactly = 0) {
            billingService.consume(any())
            billingService.acknowledge(any())
        }
    }

    @Test
    fun `consume purchases for inapp`() {
        mockQueryPurchasesResponse(skuTypeInApp, Purchase.PurchaseState.PURCHASED)
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)

        every { billingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            billingService.consume(purchaseToken)
        }
    }

    @Test
    fun `consume purchases for acknowledged subs`() {
        mockQueryPurchasesResponse(skuTypeSubs, Purchase.PurchaseState.PURCHASED, true)
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)

        productCenterManager.onAppForeground()

        verify(exactly = 0) {
            billingService.acknowledge(any())
        }
    }

    @Test
    fun `consume purchases for unacknowledged subs`() {
        mockQueryPurchasesResponse(skuTypeSubs, Purchase.PurchaseState.PURCHASED)
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)

        every { billingService.acknowledge(any()) } just Runs

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            billingService.acknowledge(purchaseToken)
        }
    }

    private fun mockQueryPurchasesResponse(
        @BillingClient.SkuType skuType: String,
        @Purchase.PurchaseState purchaseState: Int,
        isAcknowledged: Boolean = false
    ) {
        val skuDetails = mockSkuDetails(sku, skuType)
        val mapSkuDetails = mutableMapOf<String, SkuDetails>()
        mapSkuDetails[sku] = skuDetails

        val purchase = mockPurchase(sku, purchaseToken, purchaseState, isAcknowledged)
        val purchases = listOf(purchase)

        productCenterManager.mockPrivateField(fieldSkuDetails, mapSkuDetails)

        every {
            billingService.queryPurchases(captureLambda(), any())
        } answers {
            lambda<(List<Purchase>) -> Unit>().captured.invoke(
                purchases
            )
        }
    }

    private fun Any.mockPrivateField(fieldName: String, field: Any): Any {
        javaClass.declaredFields
            .filter { it.modifiers.and(Modifier.PRIVATE) > 0 || it.modifiers.and(Modifier.PROTECTED) > 0 }
            .firstOrNull { it.name == fieldName }
            ?.also { it.isAccessible = true }
            ?.set(this, field)

        return this
    }

    private fun mockSkuDetails(
        sku: String,
        @BillingClient.SkuType skuType: String
    ): SkuDetails {

        val skuDetails = mockk<SkuDetails>(relaxed = true).also {
            every { it.sku } returns sku
            every { it.type } returns skuType
        }

        return skuDetails
    }

    private fun mockPurchase(
        sku: String,
        token: String,
        @Purchase.PurchaseState purchaseState: Int,
        isAcknowledged: Boolean
    ): Purchase {

        val purchase = mockk<Purchase>(relaxed = true)

        every { purchase.sku } returns sku
        every { purchase.purchaseToken } returns token
        every { purchase.purchaseState } returns purchaseState
        every { purchase.isAcknowledged } returns isAcknowledged

        return purchase
    }
}
