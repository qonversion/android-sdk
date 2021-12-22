package com.qonversion.android.sdk.internal.billing.dataFetcher

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.PurchaseHistoryResponseListener
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.SkuDetailsResponseListener
import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.billing.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.runs
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.assertAll

@ExperimentalCoroutinesApi
class GoogleBillingDataFetcherTest {
    private lateinit var dataFetcher: GoogleBillingDataFetcherImpl
    private val mockBillingClient = mockk<BillingClient>()
    private val mockLogger = mockk<Logger>()
    private val slotQuerySkuDetailsCallback = slot<SkuDetailsResponseListener>()
    private val slotSkuDetailsParams = slot<SkuDetailsParams>()
    private val mockBillingResult = mockk<BillingResult>()
    private val mockInAppSkuFirst = mockk<SkuDetails>()
    private val mockSubsSkuFirst = mockk<SkuDetails>()
    private val mockInAppSkuSecond = mockk<SkuDetails>()
    private val mockSubsSkuSecond = mockk<SkuDetails>()
    private val firstInAppId = "superInAppId"
    private val secondInAppId = "superInAppId2"
    private val firstSubsId = "superSubsId"
    private val secondSubsId = "superSubsId2"

    @Before
    fun setUp() {
        every {
            mockLogger.debug(any())
        } just runs

        every {
            mockLogger.release(any())
        } just runs

        dataFetcher = GoogleBillingDataFetcherImpl(mockBillingClient, mockLogger)
    }

    @Test
    fun loadProducts() = runTest {
        // given
        every { mockInAppSkuFirst.sku } returns firstInAppId
        every { mockInAppSkuSecond.sku } returns secondInAppId
        every { mockSubsSkuFirst.sku } returns firstSubsId
        every { mockSubsSkuSecond.sku } returns secondSubsId

        every {
            mockBillingClient.querySkuDetailsAsync(capture(slotSkuDetailsParams), capture(slotQuerySkuDetailsCallback))
        } answers {
            when (slotSkuDetailsParams.captured.skuType) {
                BillingClient.SkuType.SUBS -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        listOf(mockSubsSkuFirst, mockSubsSkuSecond)
                    )
                }
                BillingClient.SkuType.INAPP -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        listOf(mockInAppSkuFirst, mockInAppSkuSecond)
                    )
                }
                else -> {
                    throw IllegalStateException("Unexpected SKU type")
                }
            }
        }

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        // when
        val result = dataFetcher.loadProducts(listOf(firstInAppId, secondInAppId, firstSubsId, secondSubsId))

        // then
        assertThat(result).isEqualTo(listOf(mockSubsSkuFirst, mockSubsSkuSecond, mockInAppSkuFirst, mockInAppSkuSecond))

        verify(exactly = 2) {
            mockBillingClient.querySkuDetailsAsync(any(), any())
        }
        assertThat(slotSkuDetailsParams.captured.skusList).isEqualTo(listOf(firstInAppId, secondInAppId))
    }

    @Test
    fun `load products only inApps`() = runTest {
        // given
        every { mockInAppSkuFirst.sku } returns firstInAppId
        every { mockInAppSkuSecond.sku } returns secondInAppId

        every {
            mockBillingClient.querySkuDetailsAsync(capture(slotSkuDetailsParams), capture(slotQuerySkuDetailsCallback))
        } answers {
            when (slotSkuDetailsParams.captured.skuType) {
                BillingClient.SkuType.SUBS -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        emptyList()
                    )
                }
                BillingClient.SkuType.INAPP -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        listOf(mockInAppSkuFirst, mockInAppSkuSecond)
                    )
                }
                else -> {
                    throw IllegalStateException("Unexpected SKU type")
                }
            }
        }

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        // when
        val result = dataFetcher.loadProducts(listOf(firstInAppId, secondInAppId))

        // then
        assertThat(result).isEqualTo(listOf(mockInAppSkuFirst, mockInAppSkuSecond))

        verify(exactly = 2) {
            mockBillingClient.querySkuDetailsAsync(any(), any())
        }
        assertThat(slotSkuDetailsParams.captured.skusList).isEqualTo(listOf(firstInAppId, secondInAppId))
    }

    @Test
    fun `load products only subs`() = runTest {
        // given
        every { mockSubsSkuFirst.sku } returns firstSubsId
        every { mockSubsSkuSecond.sku } returns secondSubsId

        every {
            mockBillingClient.querySkuDetailsAsync(capture(slotSkuDetailsParams), capture(slotQuerySkuDetailsCallback))
        } answers {
            when (slotSkuDetailsParams.captured.skuType) {
                BillingClient.SkuType.SUBS -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        listOf(mockSubsSkuFirst, mockSubsSkuSecond)
                    )
                }
            }
        }

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        // when
        val result = dataFetcher.loadProducts(listOf(firstSubsId, secondSubsId))

        // then
        assertThat(result).isEqualTo(listOf(mockSubsSkuFirst, mockSubsSkuSecond))

        verify(exactly = 1) {
            mockBillingClient.querySkuDetailsAsync(any(), any())
        }
        assertThat(slotSkuDetailsParams.captured.skusList).isEqualTo(listOf(firstSubsId, secondSubsId))
    }

    @Test
    fun querySkuDetails() = runTest {
        // given
        every { mockInAppSkuFirst.sku } returns firstInAppId
        every { mockInAppSkuSecond.sku } returns secondInAppId
        every { mockSubsSkuFirst.sku } returns firstSubsId
        every { mockSubsSkuSecond.sku } returns secondSubsId

        every {
            mockBillingClient.querySkuDetailsAsync(capture(slotSkuDetailsParams), capture(slotQuerySkuDetailsCallback))
        } answers {
            when (slotSkuDetailsParams.captured.skuType) {
                BillingClient.SkuType.SUBS -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        listOf(mockSubsSkuFirst, mockSubsSkuSecond)
                    )
                }
                BillingClient.SkuType.INAPP -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        listOf(mockInAppSkuFirst, mockInAppSkuSecond)
                    )
                }
                else -> {
                    throw IllegalStateException("Unexpected SKU type")
                }
            }
        }

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        val skuType = BillingClient.SkuType.SUBS
        val ids = listOf(firstSubsId, secondSubsId)

        // when
        val result = dataFetcher.querySkuDetails(skuType, ids)

        // then
        verify(exactly = 1) {
            mockBillingClient.querySkuDetailsAsync(any(), any())
        }

        assertThat(result).isEqualTo(listOf(mockSubsSkuFirst, mockSubsSkuSecond))
        assertThat(slotSkuDetailsParams.captured.skuType).isEqualTo(skuType)
        assertThat(slotSkuDetailsParams.captured.skusList).isEqualTo(ids)
    }

    @Test
    fun querySkuDetailsFailed() = runTest {
        // given
        every {
            mockBillingClient.querySkuDetailsAsync(capture(slotSkuDetailsParams), capture(slotQuerySkuDetailsCallback))
        } answers {
            when (slotSkuDetailsParams.captured.skuType) {
                BillingClient.SkuType.SUBS -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        emptyList()
                    )
                }
                BillingClient.SkuType.INAPP -> {
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockBillingResult,
                        emptyList()
                    )
                }
                else -> {
                    throw IllegalStateException("Unexpected SKU type")
                }
            }
        }
        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        val skuType = BillingClient.SkuType.SUBS
        val ids = listOf(firstSubsId, secondSubsId)

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.SkuDetailsFetching) {
            dataFetcher.querySkuDetails(skuType, ids)
        }

        // then
        verify(exactly = 1) {
            mockBillingClient.querySkuDetailsAsync(any(), any())
        }
    }

    @Test
    fun queryPurchases() = runTest {
        // given
        val slotSubsPurchasesCallback = slot<PurchasesResponseListener>()
        val slotInAppPurchasesCallback = slot<PurchasesResponseListener>()

        val mockFirstSubsPurchase = mockk<Purchase>()
        val mockSecondSubsPurchase = mockk<Purchase>()

        val mockFirstInAppPurchase = mockk<Purchase>()
        val mockSecondInAppPurchase = mockk<Purchase>()

        val expectedResult = listOf(
            mockFirstSubsPurchase,
            mockSecondSubsPurchase,
            mockFirstInAppPurchase,
            mockSecondInAppPurchase
        )

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        val mockSecondBillingResult = mockk<BillingResult>()
        every {
            mockSecondBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        every {
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, capture(slotSubsPurchasesCallback))
        } answers {
            slotSubsPurchasesCallback.captured.onQueryPurchasesResponse(
                mockBillingResult,
                listOf(mockFirstSubsPurchase, mockSecondSubsPurchase)
            )
        }

        every {
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, capture(slotInAppPurchasesCallback))
        } answers {
            slotInAppPurchasesCallback.captured.onQueryPurchasesResponse(
                mockSecondBillingResult,
                listOf(mockFirstInAppPurchase, mockSecondInAppPurchase)
            )
        }

        // when
        val result = dataFetcher.queryPurchases()

        // then
        verify(exactly = 1) {
            mockLogger.debug("queryPurchases() -> Querying purchases from cache for subs and inapp")
            mockLogger.debug("fetchPurchases() -> Querying purchases for type ${BillingClient.SkuType.SUBS}")
            mockLogger.debug("fetchPurchases() -> Querying purchases for type ${BillingClient.SkuType.INAPP}")
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, any())
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, any())
        }

        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `query purchases failed to fetch subs`() = runTest {
        testQueryPurchasesFailFlow(
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.OK
        )
    }

    @Test
    fun `query purchases failed to fetch inapps`() = runTest {
        testQueryPurchasesFailFlow(
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.ERROR
        )
    }

    @Test
    fun `query purchases failed to fetch inapps and subs`() = runTest {
        testQueryPurchasesFailFlow(
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.ERROR
        )
    }

    private fun testQueryPurchasesFailFlow(
        @BillingClient.BillingResponseCode subsResponseCode: Int,
        @BillingClient.BillingResponseCode inAppsResponseCode: Int
    ) {
        // given
        val slotSubsPurchasesCallback = slot<PurchasesResponseListener>()
        val slotInAppPurchasesCallback = slot<PurchasesResponseListener>()

        every {
            mockBillingResult.responseCode
        } returns subsResponseCode

        val mockSecondBillingResult = mockk<BillingResult>()
        every {
            mockSecondBillingResult.responseCode
        } returns inAppsResponseCode

        every {
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, capture(slotSubsPurchasesCallback))
        } answers {
            slotSubsPurchasesCallback.captured.onQueryPurchasesResponse(
                mockBillingResult,
                emptyList()
            )
        }

        every {
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, capture(slotInAppPurchasesCallback))
        } answers {
            slotInAppPurchasesCallback.captured.onQueryPurchasesResponse(
                mockSecondBillingResult,
                emptyList()
            )
        }

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.PurchasesFetching) {
            dataFetcher.queryPurchases()
        }

        // then
        verify(exactly = 1) {
            mockLogger.debug("queryPurchases() -> Querying purchases from cache for subs and inapp")
            mockLogger.debug("fetchPurchases() -> Querying purchases for type ${BillingClient.SkuType.SUBS}")
            mockLogger.debug("fetchPurchases() -> Querying purchases for type ${BillingClient.SkuType.INAPP}")
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, any())
            mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, any())
        }
    }

    @Test
    fun queryAllPurchasesHistory() = runTest {
        // given
        val slotSubsPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()
        val slotInAppPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()

        val mockFirstSubsPurchaseHistoryRecord = mockk<PurchaseHistoryRecord>()
        val mockSecondSubsPurchaseHistoryRecord = mockk<PurchaseHistoryRecord>()

        val mockFirstInAppPurchaseHistoryRecord = mockk<PurchaseHistoryRecord>()
        val mockSecondInAppPurchaseHistoryRecord = mockk<PurchaseHistoryRecord>()

        every { mockFirstSubsPurchaseHistoryRecord.skus } returns arrayListOf("1")
        every { mockSecondSubsPurchaseHistoryRecord.skus } returns arrayListOf("2")
        every { mockFirstInAppPurchaseHistoryRecord.skus } returns arrayListOf("3")
        every { mockSecondInAppPurchaseHistoryRecord.skus } returns arrayListOf("4")

        val firstInAppHistory = PurchaseHistory(BillingClient.SkuType.INAPP, mockFirstInAppPurchaseHistoryRecord)
        val secondInAppHistory = PurchaseHistory(BillingClient.SkuType.INAPP, mockSecondInAppPurchaseHistoryRecord)
        val firstSubsHistory = PurchaseHistory(BillingClient.SkuType.SUBS, mockFirstSubsPurchaseHistoryRecord)
        val secondSubsHistory = PurchaseHistory(BillingClient.SkuType.SUBS, mockSecondSubsPurchaseHistoryRecord)

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        val mockSecondBillingResult = mockk<BillingResult>()
        every {
            mockSecondBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, capture(slotSubsPurchasesHistoryCallback))
        } answers {
            slotSubsPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockBillingResult,
                listOf(mockFirstSubsPurchaseHistoryRecord, mockSecondSubsPurchaseHistoryRecord)
            )
        }

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, capture(slotInAppPurchasesHistoryCallback))
        } answers {
            slotInAppPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockSecondBillingResult,
                listOf(mockFirstInAppPurchaseHistoryRecord, mockSecondInAppPurchaseHistoryRecord)
            )
        }

        // when
        val result = dataFetcher.queryAllPurchasesHistory()

        // then
        verify(exactly = 1) {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
        }

        assertAll(
            { Assert.assertEquals(firstSubsHistory.type, result.first().type) },
            { Assert.assertEquals(firstSubsHistory.historyRecord, result.first().historyRecord) },
            { Assert.assertEquals(secondSubsHistory.type, result[1].type) },
            { Assert.assertEquals(secondSubsHistory.historyRecord, result[1].historyRecord) },
            { Assert.assertEquals(firstInAppHistory.type, result[2].type) },
            { Assert.assertEquals(firstInAppHistory.historyRecord, result[2].historyRecord) },
            { Assert.assertEquals(secondInAppHistory.type, result[3].type) },
            { Assert.assertEquals(secondInAppHistory.historyRecord, result[3].historyRecord) }
        )
    }

    @Test
    fun `query all purchases history subs failed`() = runTest {
        // given
        val slotSubsPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()
        val slotInAppPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        val mockSecondBillingResult = mockk<BillingResult>()
        every {
            mockSecondBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, capture(slotSubsPurchasesHistoryCallback))
        } answers {
            slotSubsPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockBillingResult,
                emptyList()
            )
        }

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, capture(slotInAppPurchasesHistoryCallback))
        } answers {
            slotInAppPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockSecondBillingResult,
                emptyList()
            )
        }

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.PurchasesHistoryFetching) {
            dataFetcher.queryAllPurchasesHistory()
        }

        // then
        verify(exactly = 1) {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
        }
    }

    @Test
    fun `query all purchases history inApps failed`() = runTest {
        // given
        val slotSubsPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()
        val slotInAppPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        val mockSecondBillingResult = mockk<BillingResult>()
        every {
            mockSecondBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, capture(slotSubsPurchasesHistoryCallback))
        } answers {
            slotSubsPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockBillingResult,
                emptyList()
            )
        }

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, capture(slotInAppPurchasesHistoryCallback))
        } answers {
            slotInAppPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockSecondBillingResult,
                emptyList()
            )
        }

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.PurchasesHistoryFetching) {
            dataFetcher.queryAllPurchasesHistory()
        }

        // then
        verify(exactly = 1) {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
        }
    }

    @Test
    fun `query all purchases history subs and inApps failed`() = runTest {
        // given
        val slotSubsPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()
        val slotInAppPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()

        every {
            mockBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        val mockSecondBillingResult = mockk<BillingResult>()
        every {
            mockSecondBillingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, capture(slotSubsPurchasesHistoryCallback))
        } answers {
            slotSubsPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockBillingResult,
                emptyList()
            )
        }

        every {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, capture(slotInAppPurchasesHistoryCallback))
        } answers {
            slotInAppPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                mockSecondBillingResult,
                emptyList()
            )
        }

        // when
        coAssertThatQonversionExceptionThrown(ErrorCode.PurchasesHistoryFetching) {
            dataFetcher.queryAllPurchasesHistory()
        }

        // then
        verify(exactly = 1) {
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
            mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
        }
    }

    @Test
    fun `get history from null records for skyType SUBS`() {
        // given

        // when
        val result = dataFetcher.getHistoryFromRecords(BillingClient.SkuType.SUBS, null)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `get history from null records for skyType INAPP`() {
        // given

        // when
        val result = dataFetcher.getHistoryFromRecords(BillingClient.SkuType.INAPP, null)

        // then
        assertThat(result).isEmpty()

        verify(exactly = 0) {
            mockLogger.debug(any())
        }
    }

    @Test
    fun `get history from inApp records`() {
        testQueryHistory(BillingClient.SkuType.INAPP)
    }

    @Test
    fun `get history from subs records`() {
        testQueryHistory(BillingClient.SkuType.SUBS)
    }

    private fun testQueryHistory(@BillingClient.SkuType skuType: String) {
        // given
        val mockFirstHistoryRecord = mockk<PurchaseHistoryRecord>()
        val mockSecondHistoryRecord = mockk<PurchaseHistoryRecord>()
        val historyRecords = listOf(mockFirstHistoryRecord, mockSecondHistoryRecord)

        val firstInAppHistory = PurchaseHistory(skuType, mockFirstHistoryRecord)
        val secondInAppHistory = PurchaseHistory(skuType, mockSecondHistoryRecord)

        every { mockFirstHistoryRecord.skus } returns arrayListOf("1")
        every { mockSecondHistoryRecord.skus } returns arrayListOf("2")

        // when
        val result = dataFetcher.getHistoryFromRecords(skuType, historyRecords)

        // then
        verify(exactly = 1) {
            mockLogger.debug("queryPurchaseHistoryAsync() -> purchase history " +
                    "for $skuType is retrieved [1]")
            mockLogger.debug("queryPurchaseHistoryAsync() -> purchase history " +
                    "for $skuType is retrieved [2]")
        }

        assertAll(
            { Assert.assertEquals(firstInAppHistory.type, result.first().type) },
            { Assert.assertEquals(firstInAppHistory.historyRecord, result.first().historyRecord) },
            { Assert.assertEquals(secondInAppHistory.type, result[1].type) },
            { Assert.assertEquals(secondInAppHistory.historyRecord, result[1].historyRecord) }
        )
    }

    @Test
    fun `log sku details empty list`() {
        // given
        val skuList = listOf(firstSubsId, firstInAppId)

        // when
        dataFetcher.logSkuDetails(emptyList(), skuList)

        // then
        verify(exactly = 1) {
            mockLogger.release("querySkuDetailsAsync() -> SkuDetails list for $skuList is empty.")
        }
    }

    @Test
    fun `log sku details not empty list`() {
        // given
        val skuList = listOf(firstSubsId, firstInAppId)
        val skuDetails = listOf(mockSubsSkuFirst, mockInAppSkuFirst)

        // when
        dataFetcher.logSkuDetails(skuDetails, skuList)

        // then
        skuDetails.forEach {
            verify(exactly = 1) {
                mockLogger.debug("querySkuDetailsAsync() -> $it")
            }
        }
    }
}