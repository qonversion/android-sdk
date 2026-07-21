package com.qonversion.android.sdk.dto.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QProductTest {

    @Test
    fun `one-time product exposes purchaseOptionId and null basePlanId`() {
        val consumable = QProduct("id", "store_id", "premium-option", QProduct.TYPE_CONSUMABLE)
        assertEquals("premium-option", consumable.purchaseOptionId)
        assertNull(consumable.basePlanId)

        val nonConsumable = QProduct("id", "store_id", "remove-ads", QProduct.TYPE_NON_CONSUMABLE)
        assertEquals("remove-ads", nonConsumable.purchaseOptionId)
        assertNull(nonConsumable.basePlanId)
    }

    @Test
    fun `subscription product exposes basePlanId and null purchaseOptionId`() {
        // apiType 0/1 = subscription (weekly/monthly etc.), not one-time.
        val subscription = QProduct("id", "store_id", "monthly-plan", 1)
        assertEquals("monthly-plan", subscription.basePlanId)
        assertNull(subscription.purchaseOptionId)
    }

    @Test
    fun `null store config yields null on both fields`() {
        val consumable = QProduct("id", "store_id", null, QProduct.TYPE_CONSUMABLE)
        assertNull(consumable.purchaseOptionId)
        assertNull(consumable.basePlanId)

        val subscription = QProduct("id", "store_id", null, 1)
        assertNull(subscription.purchaseOptionId)
        assertNull(subscription.basePlanId)
    }
}
