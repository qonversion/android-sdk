package com.qonversion.android.sdk.internal

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.internal.QHandledPurchasesCache
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
        // given

        // when
        val shouldHandle = handledPurchasesCache.shouldHandlePurchase(firstPurchase)

        // then
        assertThat(shouldHandle).isTrue()
    }

    @Test
    fun `non empty cache`() {
        // given
        handledPurchasesCache.saveHandledPurchase(firstPurchase)

        // when
        val shouldHandle1 = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        val shouldHandle2 = handledPurchasesCache.shouldHandlePurchase(secondPurchase)

        // then
        assertThat(shouldHandle1).isFalse()
        assertThat(shouldHandle2).isTrue()
    }

    @Test
    fun `saving multiple purchases`() {
        // given
        val handledPurchases = setOf(firstPurchase, secondPurchase)
        handledPurchasesCache.saveHandledPurchases(handledPurchases)

        // when
        val shouldHandle1 = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        val shouldHandle2 = handledPurchasesCache.shouldHandlePurchase(secondPurchase)
        val shouldHandle3 = handledPurchasesCache.shouldHandlePurchase(thirdPurchase)

        // then
        assertThat(shouldHandle1).isFalse()
        assertThat(shouldHandle2).isFalse()
        assertThat(shouldHandle3).isTrue()
    }

    @Test
    fun `sequential saving test`() {
        // given
        handledPurchasesCache.saveHandledPurchase(firstPurchase)
        handledPurchasesCache.saveHandledPurchase(secondPurchase)

        // when
        val shouldHandle1 = handledPurchasesCache.shouldHandlePurchase(firstPurchase)
        val shouldHandle2 = handledPurchasesCache.shouldHandlePurchase(secondPurchase)
        val shouldHandle3 = handledPurchasesCache.shouldHandlePurchase(thirdPurchase)

        // then
        assertThat(shouldHandle1).isFalse()
        assertThat(shouldHandle2).isFalse()
        assertThat(shouldHandle3).isTrue()
    }

    private fun mockPurchase(
        orderId: String
    ): Purchase {
        val purchase = mockk<Purchase>(relaxed = true)

        every { purchase.orderId } returns orderId

        return purchase
    }
}