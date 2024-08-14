package com.qonversion.android.sdk.internal.storage

import org.assertj.core.api.Assertions.assertThat
import android.content.SharedPreferences
import com.android.billingclient.api.BillingClient

import com.qonversion.android.sdk.internal.purchase.Purchase
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class PurchasesCacheTest {
    private val mockPrefs: SharedPreferencesCache = mockk(relaxed = true)

    private lateinit var purchasesCache: PurchasesCache

    private val purchaseKey = "purchase"
    private val onePurchaseStr = "[${generatePurchaseJson()}]"
    private val fourPurchasesStr = "[${generatePurchaseJson()},${generatePurchaseJson("2")},${generatePurchaseJson("3")},${generatePurchaseJson("4")}]"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        purchasesCache = PurchasesCache(mockPrefs)
    }

    @Nested
    inner class SavePurchase {
        @Test
        fun `should save purchase`() {
            val purchase = mockPurchase()

            purchasesCache.savePurchase(purchase)

            verifyOrder {
                mockPrefs.putString(purchaseKey, any())
            }
        }

        @Test
        fun `should not save two identical purchases`() {
            every {
                mockPrefs.getString(purchaseKey, "")
            } returns onePurchaseStr
            val purchase = mockPurchase()

            purchasesCache.savePurchase(purchase)

            verifyOrder {
                mockPrefs.putString(purchaseKey, onePurchaseStr)
            }
            val purchases = purchasesCache.loadPurchases()
            assertThat(purchases.size).isEqualTo(1)
        }

        @Test
        fun `should delete old purchases when cached purchases size is 5`() {
            every {
                mockPrefs.getString(purchaseKey, "")
            } returns fourPurchasesStr

            val fifthPurchase = mockPurchase("5")
            purchasesCache.savePurchase(fifthPurchase)
            val fourNewestPurchasesStr =
                "[${generatePurchaseJson("2")},${generatePurchaseJson("3")},${generatePurchaseJson("4")},${generatePurchaseJson("5")}]"

            verifyOrder {
                mockPrefs.putString(purchaseKey, fourNewestPurchasesStr)
            }
        }
    }

    @Nested
    inner class LoadPurchases {
        @Test
        fun `should return empty set when cache is empty`() {
            every {
                mockPrefs.getString(purchaseKey, any())
            } returns ""

            val purchases = purchasesCache.loadPurchases()
            verify(exactly = 1) {
                mockPrefs.getString(purchaseKey, any())
            }
            assertThat(purchases.size).isEqualTo(0)
        }

        @Test
        fun `should return set with 1 purchase when cache contains 1 purchase`() {
            every {
                mockPrefs.getString(purchaseKey, "")
            } returns onePurchaseStr

            val purchase = mockPurchase()

            val purchases = purchasesCache.loadPurchases()
            verify(exactly = 1) {
                mockPrefs.getString(purchaseKey, any())
            }
            assertThat(purchases.size).isEqualTo(1)
            assertThat(purchase).isEqualTo(purchases.elementAt(0))
        }

        @Test
        fun `should return empty set when cache contains invalid json string`() {
            val invalidPurchaseJsonStr = "Invalid Purchase Json Str"
            every {
                mockPrefs.getString(purchaseKey, "")
            } returns invalidPurchaseJsonStr

            val purchases = purchasesCache.loadPurchases()
            verify(exactly = 1) {
                mockPrefs.getString(purchaseKey, any())
            }
            assertThat(purchases.size).isEqualTo(0)
        }
    }

    @Nested
    inner class ClearPurchase {
        @Test
        fun `should delete purchase from set when it is existed`() {
            val emptyList = "[]"
            val purchase = mockPurchase()

            every {
                mockPrefs.getString(purchaseKey, any())
            } returns onePurchaseStr

            purchasesCache.clearPurchase(purchase)

            verifyOrder {
                mockPrefs.putString(purchaseKey, emptyList)
            }
        }

        @Test
        fun `should not delete purchase from set when it is not existed`() {
            val emptyList = "[]"
            val purchase = mockPurchase()

            purchasesCache.clearPurchase(purchase)

            verifyOrder {
                mockPrefs.putString(purchaseKey, emptyList)
            }
        }
    }

    private fun mockPurchase(originalOrderId: String = ""): Purchase {
        return Purchase(
            storeProductId = "article-test-trial",
            orderId = "GPA.3375-4436-3573-53474",
            originalOrderId = "GPA.3375-4436-3573-53474$originalOrderId",
            purchaseTime = 1611323804,
            purchaseToken = "gfegjilekkmecbonpfjiaakm.AO-J1OxQCaAn0NPlHTh5CoOiXK0p19X7qEymW9SHtssrggp7S9YafjA1oPBPlWO4Ur3W5rtyNJBzIrVoLOb5In0Jxofv4xV_7t1HaUYYd_f8xOBk7nRIY7g",
            contextKeys = listOf("test_1", "test_2")
        )
    }

    private fun generatePurchaseJson(originalOrderId: String = ""): String {
        return "{\"orderId\":\"GPA.3375-4436-3573-53474\"," +
                "\"originalOrderId\":\"GPA.3375-4436-3573-53474$originalOrderId\"," +
                "\"purchaseTime\":1611323804," +
                "\"purchaseToken\":\"gfegjilekkmecbonpfjiaakm.AO-J1OxQCaAn0NPlHTh5CoOiXK0p19X7qEymW9SHtssrggp7S9YafjA1oPBPlWO4Ur3W5rtyNJBzIrVoLOb5In0Jxofv4xV_7t1HaUYYd_f8xOBk7nRIY7g\"}"
    }
}