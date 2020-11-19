package com.qonversion.android.sdk

import android.app.Application
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.billing.BillingError
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QProductRenewState
import com.qonversion.android.sdk.logger.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class QProductCenterManagerTest {
    private val advertisingId = "advertisingId"
    private val sku = "sku"
    private val skuTypeInApp = BillingClient.SkuType.INAPP
    private val skuTypeSubs = BillingClient.SkuType.SUBS
    private val purchaseToken = "purchaseToken"
    private val qonversionProductId = "qonversionProductId"
    private val uid = "uid"
    private val permissionId = "permissionId"
    private val renewState = QProductRenewState.WillRenew
    private val startedDate = Calendar.getInstance().time
    private val expirationDate = Calendar.getInstance().time
    private val active = 1
    private val installDate: Long= 1605608753

    private val mockBillingServiceCreator: BillingServiceCreator = mockk()
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockBillingService: QonversionBillingService = mockk()

    private val isObserveMode = false

    private lateinit var productCenterManager: QProductCenterManager
    private lateinit var purchasesListener: QonversionBillingService.PurchasesListener

    private val fieldIsLaunchingFinished = "isLaunchingFinished"
    private val fieldSkuDetails = "skuDetails"
    private val fieldIsObserveMode = "isObserveMode"

    @Before
    fun setUp() {
        clearAllMocks()

        val purchasesUpdatedSlot = slot<QonversionBillingService.PurchasesListener>()
        every {
            mockBillingServiceCreator.create(capture(purchasesUpdatedSlot))
        } answers {
            purchasesListener = purchasesUpdatedSlot.captured
            mockBillingService
        }

        mockkConstructor(Utils::class)
        every { anyConstructed<Utils>().getInstallDate() } returns installDate

        productCenterManager =
            QProductCenterManager(mockContext, isObserveMode, mockRepository, mockBillingServiceCreator, mockLogger)

        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)
    }

    // onAppForeground
    @Test
    fun `handle pending purchases when launching is not finished`() {
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, false)

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

        verify (exactly = 1){
            mockBillingService.queryPurchases(any(), any())
        }

        verify(exactly = 0) {
            mockBillingService.consume(any())
            mockBillingService.acknowledge(any())

            mockRepository.purchase(any(), any(), any())

            mockContext.packageManager.getPackageInfo(
                mockContext.packageName,
                0
            ).firstInstallTime
        }
    }

    @Test
    fun `consume purchases when observe mode is true`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED)
        productCenterManager.mockPrivateField(fieldIsObserveMode, true)

        productCenterManager.onAppForeground()

        verify(exactly = 0) {
            mockBillingService.acknowledge(any())
            mockBillingService.consume(any())
        }
    }

    @Test
    fun `consume purchases with pending state`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PENDING)

        productCenterManager.onAppForeground()

        verify(exactly = 0) {
            mockBillingService.consume(any())
            mockBillingService.acknowledge(any())
        }
    }

    @Test
    fun `consume purchases for inapp`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED)
        mockSkuDetailsField(skuTypeInApp)

        every { mockBillingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.consume(purchaseToken)
        }
    }

    @Test
    fun `consume purchases for acknowledged subs`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED, true)

        productCenterManager.onAppForeground()

        verify(exactly = 0) {
            mockBillingService.acknowledge(any())
        }
    }

    @Test
    fun `consume purchases for unacknowledged subs`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED)
        mockSkuDetailsField(skuTypeSubs)

        every { mockBillingService.acknowledge(any()) } just Runs

        productCenterManager.onAppForeground()

        verify(exactly = 1) {
            mockBillingService.acknowledge(purchaseToken)
        }
    }

    @Test
    fun `handle purchases repository purchase method called with properly params`(){
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED)
        mockSkuDetailsField(skuTypeInApp)

        val installDateSlot = slot<Long>()
        val callbackSlot = slot<QonversionPermissionsCallback>()
        val entityPurchaseSlot = slot<com.qonversion.android.sdk.entity.Purchase>()
        every {
            mockRepository.purchase(capture(installDateSlot), capture(entityPurchaseSlot), capture(callbackSlot))
        } just Runs

        every { mockBillingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()
        assertThat(entityPurchaseSlot.captured.productId).isEqualTo(sku)
        assertThat(entityPurchaseSlot.captured.purchaseToken).isEqualTo(purchaseToken)
        assertThat(entityPurchaseSlot.captured.type).isEqualTo(skuTypeInApp)

        assertThat(installDateSlot.captured).isEqualTo(installDate)
    }

    @Test
    fun `handle purchases on success`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED)
        mockSkuDetailsField(skuTypeInApp)

        mockQLaunchResult()

        val mockCallback = mockk<QonversionPermissionsCallback>()
        mockPurchasingCallbacks(mockCallback, true)
        val permissions = mockQPermissions()

        val callbackSlot = slot<QonversionPermissionsCallback>()
        every {
            mockRepository.purchase(any(), any(), capture(callbackSlot))
        } answers {
            callbackSlot.captured.onSuccess(permissions)
        }

        every { mockBillingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()

        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]).isNotNull
        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]?.permissionID).isEqualTo(permissionId)
        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]?.productID).isEqualTo(qonversionProductId)
        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]?.renewState).isEqualTo(renewState)
        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]?.startedDate).isEqualTo(startedDate)
        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]?.expirationDate).isEqualTo(expirationDate)
        assertThat(productCenterManager.launchResult!!.permissions[qonversionProductId]?.active).isEqualTo(active)

        assertThat(productCenterManager.purchasingCallbacks.containsKey(sku)).isFalse()
        assertThat(productCenterManager.purchasingCallbacks.containsValue(mockCallback)).isFalse()

        verify { mockCallback.onSuccess(permissions) }
    }

    @Test
    fun `handle purchases on error`() {
        mockQueryPurchasesSuccess(Purchase.PurchaseState.PURCHASED)
        mockSkuDetailsField(skuTypeInApp)

        val mockCallback = mockk<QonversionPermissionsCallback>()
        mockPurchasingCallbacks(mockCallback, false)

        val error = QonversionError(QonversionErrorCode.BillingUnavailable)
        val callbackSlot = slot<QonversionPermissionsCallback>()
        every {
            mockRepository.purchase(any(), any(), capture(callbackSlot))
        } answers {
            callbackSlot.captured.onError(error)
        }

        every { mockBillingService.consume(any()) } just Runs

        productCenterManager.onAppForeground()

        assertThat(productCenterManager.purchasingCallbacks.containsKey(sku)).isFalse()
        assertThat(productCenterManager.purchasingCallbacks.containsValue(mockCallback)).isFalse()

        verify { mockCallback.onError(error) }
    }

    private fun mockQueryPurchasesSuccess(
        @Purchase.PurchaseState purchaseState: Int,
        isAcknowledged: Boolean = false
    ) {
        val purchase = mockPurchase(purchaseState, isAcknowledged)
        val purchases = listOf(purchase)

        every {
            mockBillingService.queryPurchases(captureLambda(), any())
        } answers {
            lambda<(List<Purchase>) -> Unit>().captured.invoke(
                purchases
            )
        }
    }

    private fun mockSkuDetailsField(@BillingClient.SkuType skuType: String) {
        val skuDetails = mockSkuDetails(skuType)
        val mapSkuDetails = mutableMapOf<String, SkuDetails>()
        mapSkuDetails[sku] = skuDetails
        productCenterManager.mockPrivateField(fieldSkuDetails, mapSkuDetails)
    }

    private fun mockPurchasingCallbacks(callback:QonversionPermissionsCallback, isSuccess: Boolean){
        if (isSuccess) {
            every {
                callback.onSuccess(any())
            } just Runs
        } else {
            every {
                callback.onError(any())
            } just Runs
        }
        productCenterManager.purchasingCallbacks[sku] = callback
    }

    private fun mockQLaunchResult() {
        productCenterManager.launchResult =  QLaunchResult(uid, Calendar.getInstance().time, mapOf(), mapOf(), mapOf())
    }

    private fun mockQPermissions(): Map<String, QPermission> {
        val permission = QPermission(
            permissionId,
            qonversionProductId,
            renewState,
            startedDate,
            expirationDate,
            active
        )
        val permissionMap = mutableMapOf<String, QPermission>()
        permissionMap[qonversionProductId] = permission

        return permissionMap
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

    private fun Any.mockPrivateField(fieldName: String, field: Any): Any {
        javaClass.declaredFields
            .filter { it.modifiers.and(Modifier.PRIVATE) > 0 || it.modifiers.and(Modifier.PROTECTED) > 0 }
            .firstOrNull { it.name == fieldName }
            ?.also { it.isAccessible = true }
            ?.set(this, field)

        return this
    }
}
