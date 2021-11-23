package com.qonversion.android.sdk.old.storage

import org.assertj.core.api.Assertions.assertThat
import android.content.SharedPreferences
import com.android.billingclient.api.BillingClient

import com.qonversion.android.sdk.old.entity.Purchase
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PurchasesCacheTest {
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private lateinit var purchasesCache: PurchasesCache

    private val purchaseKey = "purchase"
    private val onePurchaseStr = "[${generatePurchaseJson()}]"
    private val fourPurchasesStr = "[${generatePurchaseJson()},${generatePurchaseJson("2")},${generatePurchaseJson("3")},${generatePurchaseJson("4")}]"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockSharedPreferences()

        purchasesCache = PurchasesCache(mockPrefs)
    }

    @Nested
    inner class SavePurchase {
        @Test
        fun `should not save purchase when sku type is subs`() {
            val purchase = mockPurchase(BillingClient.SkuType.SUBS)

            purchasesCache.savePurchase(purchase)
            verify(exactly = 0) {
                mockEditor.putString(purchaseKey, any()).apply()
            }
        }

        @Test
        fun `should save purchase when sku type is inapp`() {
            val purchase = mockPurchase(BillingClient.SkuType.INAPP)

            purchasesCache.savePurchase(purchase)

            verifyOrder {
                mockEditor.putString(purchaseKey, onePurchaseStr)
                mockEditor.apply()
            }
        }

        @Test
        fun `should not save two identical purchases`() {
            every {
                mockPrefs.getString(purchaseKey, "")
            } returns onePurchaseStr
            val purchase = mockPurchase(BillingClient.SkuType.INAPP)

            purchasesCache.savePurchase(purchase)

            verifyOrder {
                mockEditor.putString(purchaseKey, onePurchaseStr)
                mockEditor.apply()
            }
            val purchases = purchasesCache.loadPurchases()
            assertThat(purchases.size).isEqualTo(1)
        }

        @Test
        fun `should delete old purchases when cached purchases size is 5`() {
            every {
                mockPrefs.getString(purchaseKey, "")
            } returns fourPurchasesStr

            val fifthPurchase = mockPurchase(BillingClient.SkuType.INAPP, "5")
            purchasesCache.savePurchase(fifthPurchase)
            val fourNewestPurchasesStr =
                "[${generatePurchaseJson("2")},${generatePurchaseJson("3")},${generatePurchaseJson("4")},${generatePurchaseJson("5")}]"

            verifyOrder {
                mockEditor.putString(purchaseKey, fourNewestPurchasesStr)
                mockEditor.apply()
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

            val purchase = mockPurchase(BillingClient.SkuType.INAPP)

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
            val purchase = mockPurchase(BillingClient.SkuType.INAPP)

            every {
                mockPrefs.getString(purchaseKey, any())
            } returns onePurchaseStr

            purchasesCache.clearPurchase(purchase)

            verifyOrder {
                mockEditor.putString(purchaseKey, emptyList)
                mockEditor.apply()
            }
        }

        @Test
        fun `should not delete purchase from set when it is not existed`() {
            val emptyList = "[]"
            val purchase = mockPurchase(BillingClient.SkuType.INAPP)

            purchasesCache.clearPurchase(purchase)

            verifyOrder {
                mockEditor.putString(purchaseKey, emptyList)
                mockEditor.apply()
            }
        }
    }

    private fun mockPurchase(
        @BillingClient.SkuType skuType: String,
        originalOrderId: String = ""
    ): Purchase {
        return Purchase(
            detailsToken = "AEuhp4IOz4jzn7ZFK222oIkBaHcEBKYQYmJ6QqguRvyulBm0yv0ntS6hJQx97euC1dBW",
            title = "Qonversion In-app Consumable (Qonversion Sample)",
            description = "Qonversion In-app Consumable",
            productId = "qonversion_inapp_consumable",
            type = skuType,
            originalPrice = "RUB 75.00",
            originalPriceAmountMicros = 75000000,
            priceCurrencyCode = "RUB",
            price = "75.00",
            priceAmountMicros = 75000000,
            periodUnit = 0,
            periodUnitsCount = 0,
            freeTrialPeriod = "",
            introductoryAvailable = false,
            introductoryPriceAmountMicros = 0,
            introductoryPrice = "0.00",
            introductoryPriceCycles = 0,
            introductoryPeriodUnit = 0,
            introductoryPeriodUnitsCount = 0,
            orderId = "GPA.3375-4436-3573-53474",
            originalOrderId = "GPA.3375-4436-3573-53474$originalOrderId",
            packageName = "com.qonversion.sample",
            purchaseTime = 1611323804,
            purchaseState = 1,
            purchaseToken = "gfegjilekkmecbonpfjiaakm.AO-J1OxQCaAn0NPlHTh5CoOiXK0p19X7qEymW9SHtssrggp7S9YafjA1oPBPlWO4Ur3W5rtyNJBzIrVoLOb5In0Jxofv4xV_7t1HaUYYd_f8xOBk7nRIY7g",
            acknowledged = false,
            autoRenewing = false,
            paymentMode = 0
        )
    }

    private fun mockSharedPreferences() {
        every {
            mockEditor.putString(purchaseKey, any())
        } returns mockEditor

        every {
            mockPrefs.edit()
        } returns mockEditor

        every {
            mockEditor.apply()
        } just runs
    }

    private fun generatePurchaseJson(originalOrderId: String = ""): String {
        return "{\"detailsToken\":\"AEuhp4IOz4jzn7ZFK222oIkBaHcEBKYQYmJ6QqguRvyulBm0yv0ntS6hJQx97euC1dBW\"," +
                "\"title\":\"Qonversion In-app Consumable (Qonversion Sample)\"," +
                "\"description\":\"Qonversion In-app Consumable\"," +
                "\"productId\":\"qonversion_inapp_consumable\"," +
                "\"type\":\"inapp\"," +
                "\"originalPrice\":\"RUB 75.00\"," +
                "\"originalPriceAmountMicros\":75000000," +
                "\"priceCurrencyCode\":\"RUB\"," +
                "\"price\":\"75.00\"," +
                "\"priceAmountMicros\":75000000," +
                "\"periodUnit\":0," +
                "\"periodUnitsCount\":0," +
                "\"freeTrialPeriod\":\"\"," +
                "\"introductoryAvailable\":false," +
                "\"introductoryPriceAmountMicros\":0," +
                "\"introductoryPrice\":\"0.00\"," +
                "\"introductoryPriceCycles\":0," +
                "\"introductoryPeriodUnit\":0," +
                "\"introductoryPeriodUnitsCount\":0," +
                "\"orderId\":\"GPA.3375-4436-3573-53474\"," +
                "\"originalOrderId\":\"GPA.3375-4436-3573-53474$originalOrderId\"," +
                "\"packageName\":\"com.qonversion.sample\"," +
                "\"purchaseTime\":1611323804," +
                "\"purchaseState\":1," +
                "\"purchaseToken\":\"gfegjilekkmecbonpfjiaakm.AO-J1OxQCaAn0NPlHTh5CoOiXK0p19X7qEymW9SHtssrggp7S9YafjA1oPBPlWO4Ur3W5rtyNJBzIrVoLOb5In0Jxofv4xV_7t1HaUYYd_f8xOBk7nRIY7g\"," +
                "\"acknowledged\":false," +
                "\"autoRenewing\":false," +
                "\"paymentMode\":0}"
    }
}