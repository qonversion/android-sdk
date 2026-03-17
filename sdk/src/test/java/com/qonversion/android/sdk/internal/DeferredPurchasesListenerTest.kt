package com.qonversion.android.sdk.internal

import android.os.Build
import com.qonversion.android.sdk.dto.QDeferredTransaction
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

    @Test
    fun `both listeners can be set independently`() {
        val deferredListener = mockk<QDeferredPurchasesListener>()
        val entitlementsListener = mockk<QEntitlementsUpdateListener>()

        every { mockConfig.deferredPurchasesListener = any() } just Runs
        every { mockConfig.entitlementsUpdateListener = any() } just Runs
        every { mockConfig.deferredPurchasesListener } returns deferredListener
        every { mockConfig.entitlementsUpdateListener } returns entitlementsListener

        mockConfig.deferredPurchasesListener = deferredListener
        mockConfig.entitlementsUpdateListener = entitlementsListener

        assertEquals(deferredListener, mockConfig.deferredPurchasesListener)
        assertEquals(entitlementsListener, mockConfig.entitlementsUpdateListener)
    }

    @Test
    fun `setting deferred listener does not affect entitlements listener`() {
        val deferredListener = mockk<QDeferredPurchasesListener>()
        val entitlementsListener = mockk<QEntitlementsUpdateListener>()

        every { mockConfig.entitlementsUpdateListener = any() } just Runs
        every { mockConfig.deferredPurchasesListener = any() } just Runs
        every { mockConfig.entitlementsUpdateListener } returns entitlementsListener

        mockConfig.entitlementsUpdateListener = entitlementsListener
        mockConfig.deferredPurchasesListener = deferredListener

        assertEquals(entitlementsListener, mockConfig.entitlementsUpdateListener)
    }

    @Test
    fun `DeferredTransaction data class holds correct values`() {
        val transaction = QDeferredTransaction(
            productId = "com.app.premium",
            transactionId = "token_123",
            originalTransactionId = "order_456",
            type = com.qonversion.android.sdk.dto.QDeferredTransactionType.Subscription,
            value = 9.99,
            currency = "USD"
        )

        assertEquals("com.app.premium", transaction.productId)
        assertEquals("token_123", transaction.transactionId)
        assertEquals("order_456", transaction.originalTransactionId)
        assertEquals(com.qonversion.android.sdk.dto.QDeferredTransactionType.Subscription, transaction.type)
        assertEquals(9.99, transaction.value, 0.001)
        assertEquals("USD", transaction.currency)
    }

    @Test
    fun `DeferredTransaction with null optional fields`() {
        val transaction = QDeferredTransaction(
            productId = "com.app.consumable",
            transactionId = null,
            originalTransactionId = null,
            type = com.qonversion.android.sdk.dto.QDeferredTransactionType.Consumable,
            value = 0.0,
            currency = null
        )

        assertEquals("com.app.consumable", transaction.productId)
        assertNull(transaction.transactionId)
        assertNull(transaction.originalTransactionId)
        assertEquals(com.qonversion.android.sdk.dto.QDeferredTransactionType.Consumable, transaction.type)
        assertEquals(0.0, transaction.value, 0.001)
        assertNull(transaction.currency)
    }
}
