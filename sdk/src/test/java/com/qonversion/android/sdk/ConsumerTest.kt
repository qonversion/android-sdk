package com.qonversion.android.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.entity.PurchaseHistory
import io.mockk.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

class ConsumerTest {
    private val sku = "sku"
    private val skuTypeInApp = BillingClient.SkuType.INAPP
    private val skuTypeSubs = BillingClient.SkuType.SUBS
    private val purchaseToken = "purchaseToken"
    private val fieldIsObserveMode = "isObserveMode"

    private val mockBillingService: QonversionBillingService = mockk()

    private lateinit var consumer: Consumer

    @Before
    fun setUp() {
        clearAllMocks()

        val isObserveMode = false
        consumer = Consumer(mockBillingService, isObserveMode)
    }

    @Test
    fun `consume purchases when observe mode is true`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED)
        val purchases = listOf(purchase)
        val skuDetails = mockSkuDetailsMap(skuTypeInApp)
        consumer.mockPrivateField(fieldIsObserveMode, true)

        consumer.consumePurchases(purchases, skuDetails)

        verify(exactly = 0) {
            mockBillingService.acknowledge(any())
            mockBillingService.consume(any())
        }
    }

    @Test
    fun `consume purchases with pending state`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PENDING)
        val purchases = listOf(purchase)
        val skuDetails = mockSkuDetailsMap(skuTypeInApp)

        consumer.consumePurchases(purchases, skuDetails)

        verify(exactly = 0) {
            mockBillingService.consume(any())
            mockBillingService.acknowledge(any())
        }
    }

    @Test
    fun `consume purchases for inapp`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED)
        val purchases = listOf(purchase)
        val skuDetails = mockSkuDetailsMap(skuTypeInApp)

        every { mockBillingService.consume(any()) } just Runs

        consumer.consumePurchases(purchases, skuDetails)

        verify(exactly = 1) {
            mockBillingService.consume(purchaseToken)
        }
    }

    @Test
    fun `consume purchases for acknowledged subs`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, true)
        val purchases = listOf(purchase)
        val skuDetails = mockSkuDetailsMap(skuTypeSubs)

        consumer.consumePurchases(purchases, skuDetails)

        verify(exactly = 0) {
            mockBillingService.acknowledge(any())
        }
    }

    @Test
    fun `consume purchases for unacknowledged subs`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED)
        val purchases = listOf(purchase)
        val skuDetails = mockSkuDetailsMap(skuTypeSubs)

        every { mockBillingService.acknowledge(any()) } just Runs

        consumer.consumePurchases(purchases, skuDetails)

        verify(exactly = 1) {
            mockBillingService.acknowledge(purchaseToken)
        }
    }

    @Test
    fun `consume history records when observe mode is true`(){
        val record = mockPurchaseHistoryRecord()
        val purchaseHistory = PurchaseHistory(skuTypeInApp, record)
        val purchaseHistoryList = listOf(purchaseHistory)

        consumer.mockPrivateField(fieldIsObserveMode, true)

        consumer.consumeHistoryRecords(purchaseHistoryList)

        verify(exactly = 0) {
            mockBillingService.acknowledge(any())
            mockBillingService.consume(any())
        }
    }

    @Test
    fun `consume history records for inapp`(){
        val record = mockPurchaseHistoryRecord()
        val purchaseHistory = PurchaseHistory(skuTypeInApp, record)
        val purchaseHistoryList = listOf(purchaseHistory)

        every { mockBillingService.consume(any()) } just Runs

        consumer.consumeHistoryRecords(purchaseHistoryList)

        verify(exactly = 1) {
            mockBillingService.consume(purchaseToken)
        }
    }

    @Test
    fun `consume history records for subs`(){
        val record = mockPurchaseHistoryRecord()
        val purchaseHistory = PurchaseHistory(skuTypeSubs, record)
        val purchaseHistoryList = listOf(purchaseHistory)

        every { mockBillingService.acknowledge(any()) } just Runs

        consumer.consumeHistoryRecords(purchaseHistoryList)

        verify(exactly = 1) {
            mockBillingService.acknowledge(purchaseToken)
        }
    }

    private fun mockSkuDetailsMap(@BillingClient.SkuType skuType: String): Map<String, SkuDetails> {
        val skuDetails = mockSkuDetails(skuType)
        val mapSkuDetails = mutableMapOf<String, SkuDetails>()
        mapSkuDetails[sku] = skuDetails

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
        isAcknowledged: Boolean = false
    ): Purchase {

        val purchase = mockk<Purchase>(relaxed = true)

        every { purchase.sku } returns sku
        every { purchase.purchaseToken } returns purchaseToken
        every { purchase.purchaseState } returns purchaseState
        every { purchase.isAcknowledged } returns isAcknowledged

        return purchase
    }

    private fun mockPurchaseHistoryRecord(): PurchaseHistoryRecord {
        val purchaseHistoryRecord = mockk<PurchaseHistoryRecord>(relaxed = true)

        every { purchaseHistoryRecord.sku } returns sku
        every { purchaseHistoryRecord.purchaseToken } returns purchaseToken

        return purchaseHistoryRecord
    }

    private fun Any.mockPrivateField(fieldName: String, field: Any) {
        javaClass.declaredFields
            .filter { it.modifiers.and(Modifier.PRIVATE) > 0 || it.modifiers.and(Modifier.PROTECTED) > 0 }
            .firstOrNull { it.name == fieldName }
            ?.also { it.isAccessible = true }
            ?.set(this, field)
    }
}