package com.qonversion.android.sdk.dto.entitlements

import org.junit.Assert.assertEquals
import org.junit.Test

class QEntitlementSourceTest {

    @Test
    fun `fromKey maps every backend source key`() {
        assertEquals(QEntitlementSource.AppStore, QEntitlementSource.fromKey("appstore"))
        assertEquals(QEntitlementSource.PlayStore, QEntitlementSource.fromKey("playstore"))
        assertEquals(QEntitlementSource.Stripe, QEntitlementSource.fromKey("stripe"))
        assertEquals(QEntitlementSource.Paddle, QEntitlementSource.fromKey("paddle"))
        assertEquals(QEntitlementSource.Manual, QEntitlementSource.fromKey("manual"))
        assertEquals(QEntitlementSource.Unknown, QEntitlementSource.fromKey("unknown"))
    }

    @Test
    fun `fromKey falls back to Unknown for an unexpected key`() {
        assertEquals(QEntitlementSource.Unknown, QEntitlementSource.fromKey("foo"))
    }
}
