package com.qonversion.android.sdk.internal.converter

import android.util.Pair
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.qonversion.android.sdk.internal.extractor.SkuDetailsTokenExtractor
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertAll
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GooglePurchaseConverterTest {
    private val mockExtractor: SkuDetailsTokenExtractor = mockk(relaxed = true)
    private lateinit var purchaseConverter: GooglePurchaseConverter

    private val mockToken = "XXXXXXX"
    private val mockOrderId = "GPA.0000-0000-0000-0000"
    private val weeklySku = "subs_weekly"
    private val annualSku = "subs_annual"

    @Before
    fun setUp() {
        clearAllMocks()

        purchaseConverter = GooglePurchaseConverter(mockExtractor)
    }

    @Test
    fun `should convert purchases when purchase and skuDetails skus match`() {
        // given
        val spykPurchaseConverter = spyk(purchaseConverter, recordPrivateCalls = true)

        val mockWeeklySkuDetails = mockSubsSkuDetails(weeklySku)
        val mockWeeklyPurchase = mockSubsPurchase(weeklySku)
        val mockAnnualSkuDetails = mockSubsSkuDetails(annualSku)
        val mockAnnualPurchase = mockSubsPurchase(annualSku)

        val purchasesInfoMap = mapOf(
            weeklySku to mockWeeklySkuDetails,
            annualSku to mockAnnualSkuDetails
        )
        val purchases = listOf(mockWeeklyPurchase, mockAnnualPurchase)

        // when
        val result = spykPurchaseConverter.convertPurchases(purchasesInfoMap, purchases)
        verifyOrder {
            spykPurchaseConverter.convertPurchase(
                Pair.create(
                    mockWeeklySkuDetails,
                    mockWeeklyPurchase
                )
            )
            spykPurchaseConverter.convertPurchase(
                Pair.create(
                    mockAnnualSkuDetails,
                    mockAnnualPurchase
                )
            )
        }

        // then
        assertThat(result.size).isEqualTo(2)
    }

    @Test
    fun `should not convert purchases when purchase and skuDetails skus don't match`() {
        // given
        val spykPurchaseConverter = spyk(purchaseConverter, recordPrivateCalls = true)

        val wrongSku = "wrong_subs_weekly"

        val mockWeeklySkuDetails = mockSubsSkuDetails(weeklySku)
        val mockWeeklyPurchase = mockSubsPurchase(wrongSku)

        val map = mapOf(weeklySku to mockWeeklySkuDetails)
        val purchases = listOf(mockWeeklyPurchase)

        // when
        val result = spykPurchaseConverter.convertPurchases(map, purchases)

        // then
        verify(exactly = 0) {
            spykPurchaseConverter.convertPurchase(
                any()
            )
        }
        assertThat(result.size).isEqualTo(0)
    }

    @Test
    fun `shouldn't convert incorrect purchase without sku`() {
        // given
        val mockWeeklySkuDetails = mockSubsSkuDetails()
        val mockWeeklyPurchase = mockIncorrectSubsPurchase()
        val pair = Pair.create(
            mockWeeklySkuDetails,
            mockWeeklyPurchase
        )

        // when
        val result = purchaseConverter.convertPurchase(pair)

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `should convert subs purchase correctly`() {
        // given
        val spykPurchaseConverter = spyk(purchaseConverter, recordPrivateCalls = true)

        val mockWeeklySkuDetails = mockSubsSkuDetails()
        val mockWeeklyPurchase = mockSubsPurchase()

        every {
            mockExtractor.extract(mockSubsSkuDetailsJson())
        } returns mockToken

        // when
        val result = spykPurchaseConverter.convertPurchase(
            Pair.create(
                mockWeeklySkuDetails,
                mockWeeklyPurchase
            )
        )

        // then
        Assert.assertNotNull(result)
        assertCommonSubsFields(result)
        // Assert intro and trial period fields of subscription
        assertAll(
            "Converted purchase contains incorrect fields:",
            { assertEquals(0, result!!.freeTrialPeriod.length, "freeTrialPeriod should be empty")},
            { assertEquals(true, result!!.introductoryAvailable, "introductoryAvailable should be true") },
            { assertEquals(85000000, result!!.introductoryPriceAmountMicros) },
            { assertEquals("85.00", result!!.introductoryPrice) },
            { assertEquals(1, result!!.introductoryPriceCycles, "introductoryPriceCycles is incorrect") },
            { assertEquals(0, result!!.introductoryPeriodUnit, "introductoryPeriodUnit is incorrect") },
            { assertEquals(3, result!!.introductoryPeriodUnitsCount, "introductoryPeriodUnitsCount should be null")},
            { assertEquals(0, result!!.paymentMode,"paymentMode is incorrect") }
        )
    }

    @Test
    fun `should convert subs purchase with trial correctly`() {
        // given
        val spykPurchaseConverter = spyk(purchaseConverter, recordPrivateCalls = true)

        val weeklySku = "subs_weekly"

        val mockWeeklySkuDetails = mockSubsSkuDetails(weeklySku, freeTrialPeriod = "P9W2D")
        val mockWeeklyPurchase = mockSubsPurchase(weeklySku)
        every {
            mockExtractor.extract(mockSubsSkuDetailsJson(weeklySku, freeTrialPeriod = "P9W2D"))
        } returns mockToken

        // when
        val result = spykPurchaseConverter.convertPurchase(
            Pair.create(
                mockWeeklySkuDetails,
                mockWeeklyPurchase
            )
        )

        // then
        Assert.assertNotNull(result)
        assertCommonSubsFields(result)
        // Assert intro and trial period fields of subscription
        assertAll(
            "Converted purchase contains incorrect fields",
            { assertEquals("P9W2D", result!!.freeTrialPeriod, "freeTrialPeriod is incorrect") },
            { assertEquals(true, result!!.introductoryAvailable, "introductoryAvailable should be true") },
            { assertEquals(85000000, result!!.introductoryPriceAmountMicros) },
            { assertEquals("0.0", result!!.introductoryPrice) },
            { assertEquals(0, result!!.introductoryPriceCycles, "introductoryPriceCycles is incorrect") },
            { assertEquals(0, result!!.introductoryPeriodUnit, "introductoryPriceCycles is incorrect") },
            { assertEquals(65, result!!.introductoryPeriodUnitsCount) },
            { assertEquals(2, result!!.paymentMode, "paymentMode is incorrect") }
        )
    }

    private fun assertCommonSubsFields(purchase: Purchase?) {
        assertAll(
            "Converted purchase contains incorrect fields",
            { assertEquals(mockToken, purchase!!.detailsToken, "detailsToken token is incorrect") },
            { assertEquals("Qonversion Subs", purchase!!.title) },
            { assertEquals("Weekly", purchase!!.description) },
            { assertEquals(weeklySku, purchase!!.productId) },
            { assertEquals("subs", purchase!!.type) },
            { assertEquals("RUB 439.00", purchase!!.originalPrice) },
            { assertEquals(439000000, purchase!!.originalPriceAmountMicros) },
            { assertEquals("RUB", purchase!!.priceCurrencyCode) },
            { assertEquals("439.00", purchase!!.price) },
            { assertEquals(439000000, purchase!!.priceAmountMicros) },
            { assertEquals(1, purchase!!.periodUnit, "periodUnit is incorrect") },
            { assertEquals(1, purchase!!.periodUnitsCount, "periodUnitsCount is incorrect") },
            { assertEquals(mockOrderId, purchase!!.orderId, "orderId is incorrect") },
            { assertEquals(mockOrderId, purchase!!.originalOrderId, "originalOrderId is incorrect") },
            { assertEquals("com.qonversion.sample", purchase!!.packageName) },
            { assertEquals(1631867965, purchase!!.purchaseTime) },
            { assertEquals( 1, purchase!!.purchaseState, "purchaseState is incorrect") },
            { assertEquals(mockToken, purchase!!.purchaseToken, "purchaseToken is incorrect") },
            { assertEquals(true, purchase!!.acknowledged, "acknowledged should be true") },
            { assertEquals(true, purchase!!.autoRenewing, "autoRenewing should be true") }
        )
    }

    @Test
    fun `should get correct units type from period`() {
        // given
        val dailyPeriod = "P1D"
        val weeklyPeriod = "P1W"
        val monthlyPeriod = "P1M"
        val annualPeriod = "P1Y"
        val incorrectPeriod = "P1S"

        val mockWeeklySkuDetails = mockSubsSkuDetails(subsPeriod = weeklyPeriod)
        val mockAnnualSkuDetails = mockSubsSkuDetails(subsPeriod = annualPeriod)
        val mockMonthlySkuDetails = mockSubsSkuDetails(subsPeriod = monthlyPeriod)
        val mockDailySkuDetails = mockSubsSkuDetails(subsPeriod = dailyPeriod)
        val mockIncorrectSkuDetails = mockSubsSkuDetails(subsPeriod = incorrectPeriod)
        val mockSubsPurchase = mockSubsPurchase()

        val skuDetailsMap = mapOf(
            weeklyPeriod to mockWeeklySkuDetails,
            annualPeriod to mockAnnualSkuDetails,
            monthlyPeriod to mockMonthlySkuDetails,
            dailyPeriod to mockDailySkuDetails,
            incorrectPeriod to mockIncorrectSkuDetails
        )

        skuDetailsMap.forEach { entry ->
            val skuDetails = entry.value
            val periodUnit = when (entry.key) {
                annualPeriod -> 3
                monthlyPeriod -> 2
                weeklyPeriod -> 1
                dailyPeriod -> 0
                else -> null
            }

            // when
            val result = purchaseConverter.convertPurchase(
                Pair.create(
                    skuDetails,
                    mockSubsPurchase
                )
            )

            // then
            assertEquals(periodUnit, result!!.periodUnit)
        }
    }

    @Test
    fun `should convert inapp purchase correctly`() {
        // given
        val spykPurchaseConverter = spyk(purchaseConverter, recordPrivateCalls = true)

        val mockSkuDetails = mockInAppSkuDetails()
        val mockPurchase = mockInAppPurchase()

        every {
            mockExtractor.extract(mockInAppSkuDetailsJson())
        } returns mockToken

        // when
        val result = spykPurchaseConverter.convertPurchase(
            Pair.create(
                mockSkuDetails,
                mockPurchase
            )
        )

        // then
        Assert.assertNotNull(result)
        assertAll(
            "Converted purchase contains incorrect fields",
            { assertEquals(mockToken, result!!.detailsToken, "detailsToken is incorrect") },
            { assertEquals("Qonversion In-app", result!!.title) },
            { assertEquals("Consumable", result!!.description) },
            { assertEquals("qonversion_inapp_consumable", result!!.productId) },
            { assertEquals("inapp", result!!.type) },
            { assertEquals("RUB 75.00", result!!.originalPrice) },
            { assertEquals(75000000, result!!.originalPriceAmountMicros) },
            { assertEquals("RUB", result!!.priceCurrencyCode) },
            { assertEquals("75.00", result!!.price) },
            { assertEquals(75000000, result!!.priceAmountMicros) },
            { assertEquals(null, result!!.periodUnit, "periodUnit is incorrect") },
            { assertEquals(null, result!!.periodUnitsCount, "periodUnitsCount is incorrect") },
            { assertEquals(0, result!!.freeTrialPeriod.length, "freeTrialPeriod should be empty")},
            { assertEquals(false, result!!.introductoryAvailable, "introductoryAvailable should be false") },
            { assertEquals(0, result!!.introductoryPriceAmountMicros, "introductoryPriceAmountMicros is incorrect") },
            { assertEquals("0.00", result!!.introductoryPrice) },
            { assertEquals(0, result!!.introductoryPriceCycles, "introductoryPriceCycles is incorrect") },
            { assertEquals(0, result!!.introductoryPeriodUnit, "introductoryPeriodUnit is incorrect") },
            { assertEquals(null, result!!.introductoryPeriodUnitsCount, "introductoryPeriodUnitsCount should be null")},
            { assertEquals(mockOrderId, result!!.orderId, "orderId is incorrect") },
            { assertEquals(mockOrderId, result!!.originalOrderId, "originalOrderId is incorrect") },
            { assertEquals("com.qonversion.sample", result!!.packageName) },
            { assertEquals(1632238801, result!!.purchaseTime) },
            { assertEquals( 1, result!!.purchaseState, "purchaseState is incorrect") },
            { assertEquals(mockToken, result!!.purchaseToken, "purchaseToken is incorrect") },
            { assertEquals(true, result!!.acknowledged, "acknowledged should be true") },
            { assertEquals(false, result!!.autoRenewing, "autoRenewing should be false") },
            { assertEquals(0, result!!.paymentMode, "paymentMode is incorrect") }
        )
    }
}