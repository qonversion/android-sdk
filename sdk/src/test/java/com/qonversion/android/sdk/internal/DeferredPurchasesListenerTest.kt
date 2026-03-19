package com.qonversion.android.sdk.internal

import android.os.Build
import com.qonversion.android.sdk.dto.QDeferredTransaction
import com.qonversion.android.sdk.dto.QDeferredTransactionType
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.listeners.QDeferredPurchasesListener
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
internal class DeferredPurchasesListenerTest {

    private val mockConfig = mockk<InternalConfig>(relaxed = true)

    @Before
    fun setUp() {
        clearAllMocks()
    }

    // -- Adapter pattern tests (review feedback: Task 6) --

    @Test
    fun `adapter forwards entitlements from purchaseResult to legacy listener`() {
        // The adapter extracts entitlements from QPurchaseResult and passes them
        // to the wrapped QEntitlementsUpdateListener - this is the core adapter behavior.
        val legacyListener = mockk<QEntitlementsUpdateListener>()
        every { legacyListener.onEntitlementsUpdated(any()) } just Runs

        val adapter = EntitlementsUpdateListenerAdapter(legacyListener)

        val entitlements = mapOf("premium" to mockk<QEntitlement>())
        val purchaseResult = mockk<QPurchaseResult>()
        every { purchaseResult.entitlements } returns entitlements

        val transaction = QDeferredTransaction(
            productId = "com.app.premium",
            transactionId = "token_123",
            originalTransactionId = "order_456",
            type = QDeferredTransactionType.Subscription,
            value = 9.99,
            currency = "USD"
        )

        adapter.deferredPurchaseCompleted(transaction, purchaseResult)

        verify(exactly = 1) { legacyListener.onEntitlementsUpdated(entitlements) }
    }

    @Test
    fun `adapter forwards empty entitlements for consumable products`() {
        // Consumable products may have no entitlements - the adapter should still
        // forward the empty map to the legacy listener.
        val legacyListener = mockk<QEntitlementsUpdateListener>()
        every { legacyListener.onEntitlementsUpdated(any()) } just Runs

        val adapter = EntitlementsUpdateListenerAdapter(legacyListener)

        val purchaseResult = mockk<QPurchaseResult>()
        every { purchaseResult.entitlements } returns emptyMap()

        val transaction = QDeferredTransaction(
            productId = "com.app.coins",
            transactionId = "token_456",
            originalTransactionId = null,
            type = QDeferredTransactionType.Consumable,
            value = 0.0,
            currency = null
        )

        adapter.deferredPurchaseCompleted(transaction, purchaseResult)

        verify(exactly = 1) { legacyListener.onEntitlementsUpdated(emptyMap()) }
    }

    @Test
    fun `adapter implements QDeferredPurchasesListener interface`() {
        // The adapter must be a valid QDeferredPurchasesListener so it can replace
        // the entitlementsUpdateListener everywhere internally.
        val legacyListener = mockk<QEntitlementsUpdateListener>()
        val adapter = EntitlementsUpdateListenerAdapter(legacyListener)

        assertTrue(adapter is QDeferredPurchasesListener)
    }

    // -- Config tests --

    @Test
    fun `setDeferredPurchasesListener stores listener in config`() {
        val listener = mockk<QDeferredPurchasesListener>()
        every { mockConfig.deferredPurchasesListener = any() } just Runs
        every { mockConfig.deferredPurchasesListener } returns listener

        mockConfig.deferredPurchasesListener = listener

        assertEquals(listener, mockConfig.deferredPurchasesListener)
        verify { mockConfig.deferredPurchasesListener = listener }
    }

    @Test
    fun `setDeferredPurchasesListener to null clears listener`() {
        every { mockConfig.deferredPurchasesListener = any() } just Runs
        every { mockConfig.deferredPurchasesListener } returns null

        mockConfig.deferredPurchasesListener = null

        assertNull(mockConfig.deferredPurchasesListener)
    }

    // -- DeferredTransaction data class tests --

    @Test
    fun `DeferredTransaction data class holds correct values`() {
        val transaction = QDeferredTransaction(
            productId = "com.app.premium",
            transactionId = "token_123",
            originalTransactionId = "order_456",
            type = QDeferredTransactionType.Subscription,
            value = 9.99,
            currency = "USD"
        )

        assertEquals("com.app.premium", transaction.productId)
        assertEquals("token_123", transaction.transactionId)
        assertEquals("order_456", transaction.originalTransactionId)
        assertEquals(QDeferredTransactionType.Subscription, transaction.type)
        assertEquals(9.99, transaction.value, 0.001)
        assertEquals("USD", transaction.currency)
    }

    @Test
    fun `DeferredTransaction with null optional fields`() {
        val transaction = QDeferredTransaction(
            productId = "com.app.consumable",
            transactionId = null,
            originalTransactionId = null,
            type = QDeferredTransactionType.Consumable,
            value = 0.0,
            currency = null
        )

        assertEquals("com.app.consumable", transaction.productId)
        assertNull(transaction.transactionId)
        assertNull(transaction.originalTransactionId)
        assertEquals(QDeferredTransactionType.Consumable, transaction.type)
        assertEquals(0.0, transaction.value, 0.001)
        assertNull(transaction.currency)
    }
}
