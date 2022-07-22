package com.qonversion.android.sdk

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.storage.EntitlementsCache
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

internal class EntitlementsManagerTest {

    private val testQonversionUserId = "test user id"
    private val mockEntitlementsCache = mockk<EntitlementsCache>()

    private lateinit var entitlementsManager: EntitlementsManager

    @BeforeEach
    fun setUp() {
        entitlementsManager = spyk(EntitlementsManager(mockk(), mockEntitlementsCache, mockk(relaxed = true))) {
            every { cacheEntitlementsForUser(testQonversionUserId, any()) } just runs
        }
    }

    @Nested
    inner class GrantEntitlementsAfterFailedPurchaseTracking {

        private val testEntitlementId1 = "1"
        private val testEntitlementId2 = "2"
        private val testEntitlementId3 = "3"
        private val testEntitlementId4 = "4"
        private val testEntitlement1 = mockk<QEntitlement> {
            every { permissionID } returns testEntitlementId1
        }
        private val testEntitlement2 = mockk<QEntitlement> {
            every { permissionID } returns testEntitlementId2
        }
        private val testEntitlement3 = mockk<QEntitlement> {
            every { permissionID } returns testEntitlementId3
        }
        private val testEntitlement4 = mockk<QEntitlement> {
            every { permissionID } returns testEntitlementId4
        }
        private val mockPurchase = mockk<Purchase>()
        private val mockPurchasedProduct = mockk<QProduct>()

        @BeforeEach
        fun setUp() {
            every { entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, any()) } just runs
            every { entitlementsManager.createEntitlement(
                testEntitlementId1,
                mockPurchase,
                mockPurchasedProduct
            ) } returns testEntitlement1
            every { entitlementsManager.createEntitlement(
                testEntitlementId2,
                mockPurchase,
                mockPurchasedProduct
            ) } returns testEntitlement2
            every { entitlementsManager.createEntitlement(
                testEntitlementId3,
                mockPurchase,
                mockPurchasedProduct
            ) } returns testEntitlement3
            every { entitlementsManager.createEntitlement(
                testEntitlementId4,
                mockPurchase,
                mockPurchasedProduct
            ) } returns testEntitlement4
        }

        @Test
        fun `mixing different entitlements`() {
            // given
            every { mockPurchasedProduct.permissionIds } returns listOf(
                testEntitlementId1,
                testEntitlementId2
            )
            every {
                mockEntitlementsCache.getActualStoredValue(true)
            } returns listOf(testEntitlement3, testEntitlement4)

            // when
            val result = entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                testQonversionUserId, mockPurchase, mockPurchasedProduct
            )

            // then
            verifyOrder {
                entitlementsManager.createEntitlement(testEntitlementId1, mockPurchase, mockPurchasedProduct)
                entitlementsManager.createEntitlement(testEntitlementId2, mockPurchase, mockPurchasedProduct)
                mockEntitlementsCache.getActualStoredValue(true)
                entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, result)
            }
            assertThat(result).isEqualTo(listOf(
                testEntitlement1,
                testEntitlement2,
                testEntitlement3,
                testEntitlement4
            ))
        }

        @Test
        fun `no existing entitlements cache`() {
            // given
            every { mockPurchasedProduct.permissionIds } returns listOf(
                testEntitlementId1,
                testEntitlementId2
            )
            every {
                mockEntitlementsCache.getActualStoredValue(true)
            } returns null

            // when
            val result = entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                testQonversionUserId, mockPurchase, mockPurchasedProduct
            )

            // then
            verifyOrder {
                entitlementsManager.createEntitlement(testEntitlementId1, mockPurchase, mockPurchasedProduct)
                entitlementsManager.createEntitlement(testEntitlementId2, mockPurchase, mockPurchasedProduct)
                mockEntitlementsCache.getActualStoredValue(true)
                entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, result)
            }
            assertThat(result).isEqualTo(listOf(
                testEntitlement1,
                testEntitlement2
            ))
        }

        @Test
        fun `no new granting entitlements`() {
            // given
            every { mockPurchasedProduct.permissionIds } returns emptyList()
            every {
                mockEntitlementsCache.getActualStoredValue(true)
            } returns listOf(testEntitlement3, testEntitlement4)

            // when
            val result = entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                testQonversionUserId, mockPurchase, mockPurchasedProduct
            )

            // then
            verifyOrder {
                mockEntitlementsCache.getActualStoredValue(true)
                entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, result)
            }
            verify(exactly = 0) {
                entitlementsManager.createEntitlement(any(), mockPurchase, mockPurchasedProduct)
            }
            assertThat(result).isEqualTo(listOf(
                testEntitlement3,
                testEntitlement4
            ))
        }

        @Test
        fun `entitlement with the same id is cached but not active`() {
            // given
            every { mockPurchasedProduct.permissionIds } returns listOf(
                testEntitlementId1,
                testEntitlementId2
            )
            every { testEntitlement2.isActive } returns true
            val cachedEntitlement2 = mockk<QEntitlement> {
                every { permissionID } returns testEntitlementId2
                every { isActive } returns false
            }
            every {
                mockEntitlementsCache.getActualStoredValue(true)
            } returns listOf(cachedEntitlement2, testEntitlement3)

            // when
            val result = entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                testQonversionUserId, mockPurchase, mockPurchasedProduct
            )

            // then
            verifyOrder {
                entitlementsManager.createEntitlement(testEntitlementId1, mockPurchase, mockPurchasedProduct)
                entitlementsManager.createEntitlement(testEntitlementId2, mockPurchase, mockPurchasedProduct)
                mockEntitlementsCache.getActualStoredValue(true)
                entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, result)
            }
            assertThat(result).isEqualTo(listOf(
                testEntitlement1,
                testEntitlement2,
                testEntitlement3
            ))
        }

        @Test
        fun `entitlement with the same id is cached and is active but expires earlier`() {
            // given
            every { mockPurchasedProduct.permissionIds } returns listOf(
                testEntitlementId1,
                testEntitlementId2
            )
            every { testEntitlement2.isActive } returns true
            every { testEntitlement2.expirationDate } returns Date(20)
            val cachedEntitlement2 = mockk<QEntitlement> {
                every { permissionID } returns testEntitlementId2
                every { isActive } returns true
                every { expirationDate } returns Date(10)
            }
            every {
                mockEntitlementsCache.getActualStoredValue(true)
            } returns listOf(cachedEntitlement2, testEntitlement3)

            // when
            val result = entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                testQonversionUserId, mockPurchase, mockPurchasedProduct
            )

            // then
            verifyOrder {
                entitlementsManager.createEntitlement(testEntitlementId1, mockPurchase, mockPurchasedProduct)
                entitlementsManager.createEntitlement(testEntitlementId2, mockPurchase, mockPurchasedProduct)
                mockEntitlementsCache.getActualStoredValue(true)
                entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, result)
            }
            assertThat(result).isEqualTo(listOf(
                testEntitlement1,
                testEntitlement2,
                testEntitlement3
            ))
        }

        @Test
        fun `entitlement with the same id is cached and is active and expires later`() {
            // given
            every { mockPurchasedProduct.permissionIds } returns listOf(
                testEntitlementId1,
                testEntitlementId2
            )
            every { testEntitlement2.isActive } returns true
            every { testEntitlement2.expirationDate } returns Date(20)
            val cachedEntitlement2 = mockk<QEntitlement> {
                every { permissionID } returns testEntitlementId2
                every { isActive } returns true
                every { expirationDate } returns Date(30)
            }
            every {
                mockEntitlementsCache.getActualStoredValue(true)
            } returns listOf(cachedEntitlement2, testEntitlement3)

            // when
            val result = entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                testQonversionUserId, mockPurchase, mockPurchasedProduct
            )

            // then
            verifyOrder {
                entitlementsManager.createEntitlement(testEntitlementId1, mockPurchase, mockPurchasedProduct)
                entitlementsManager.createEntitlement(testEntitlementId2, mockPurchase, mockPurchasedProduct)
                mockEntitlementsCache.getActualStoredValue(true)
                entitlementsManager.cacheEntitlementsForUser(testQonversionUserId, result)
            }
            assertThat(result).isEqualTo(listOf(
                testEntitlement1,
                cachedEntitlement2,
                testEntitlement3
            ))
        }
    }
}