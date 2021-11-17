package com.qonversion.android.sdk

import com.android.billingclient.api.Purchase
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class QHandledPurchasesCacheTest {
    private lateinit var handledPurchasesCache: QHandledPurchasesCache

    private val firstPurchase = mockPurchase("1")
    private val secondPurchase = mockPurchase("2")
    private val thirdPurchase = mockPurchase("3")

    @Before
    fun setUp() {
        handledPurchasesCache = QHandledPurchasesCache()
    }

    @Test
    fun `empty cache`() {
        val shouldHandle = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        assertThat(shouldHandle).isTrue()
    }

    @Test
    fun `non empty cache`() {
        handledPurchasesCache.saveHandledPurchase(firstPurchase)

        val shouldHandle1 = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        val shouldHandle2 = handledPurchasesCache.shouldHandlePurchase(secondPurchase)

        assertThat(shouldHandle1).isFalse()
        assertThat(shouldHandle2).isTrue()
    }

    @Test
    fun `saving multiple purchases`() {
        val handledPurchases = setOf(firstPurchase, secondPurchase)
        handledPurchasesCache.saveHandledPurchases(handledPurchases)

        val shouldHandle1 = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        val shouldHandle2 = handledPurchasesCache.shouldHandlePurchase(secondPurchase)
        val shouldHandle3 = handledPurchasesCache.shouldHandlePurchase(thirdPurchase)

        assertThat(shouldHandle1).isFalse()
        assertThat(shouldHandle2).isFalse()
        assertThat(shouldHandle3).isTrue()
    }

    @Test
    fun `sequential test`() {
        val shouldHandle1 = handledPurchasesCache.shouldHandlePurchase(firstPurchase)

        handledPurchasesCache.saveHandledPurchase(firstPurchase)
        val shouldHandle1Again = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        val shouldHandle2 = handledPurchasesCache.shouldHandlePurchase(secondPurchase)

        handledPurchasesCache.saveHandledPurchase(secondPurchase)
        val shouldHandle2Again = handledPurchasesCache.shouldHandlePurchase(secondPurchase)
        val shouldHandle3 = handledPurchasesCache.shouldHandlePurchase(thirdPurchase)

        handledPurchasesCache.saveHandledPurchase(thirdPurchase)
        val shouldHandle3Again = handledPurchasesCache.shouldHandlePurchase(thirdPurchase)

        assertThat(shouldHandle1 && shouldHandle2 && shouldHandle3).isTrue()
        assertThat(shouldHandle1Again || shouldHandle2Again || shouldHandle3Again).isFalse()
    }

    private fun mockPurchase(
        orderId: String
    ): Purchase {
        val purchase = mockk<Purchase>(relaxed = true)

        every { purchase.orderId } returns orderId

        return purchase
    }
}