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
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.runs
import io.mockk.verify
import java.lang.IllegalStateException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class GoogleBillingDataFetcherTest {

    private lateinit var dataFetcher: GoogleBillingDataFetcherImpl
    private val mockBillingClient = mockk<BillingClient>()
    private val mockLogger = mockk<Logger>()
    private val slotQuerySkuDetailsCallback = slot<SkuDetailsResponseListener>()
    private val slotSkuDetailsParams = mutableListOf<SkuDetailsParams>()
    private val mockSubsBillingResult = mockk<BillingResult>()
    private val mockInAppsBillingResult = mockk<BillingResult>()
    private val mockSubsSkuDetailsFirst = mockk<SkuDetails>()
    private val mockSubsSkuDetailsSecond = mockk<SkuDetails>()
    private val mockInAppSkuDetailsFirst = mockk<SkuDetails>()
    private val mockInAppSkuDetailsSecond = mockk<SkuDetails>()
    private val firstInAppId = "superInAppId"
    private val secondInAppId = "superInAppId2"
    private val firstSubsId = "superSubsId"
    private val secondSubsId = "superSubsId2"
    private val slotDebugLogMessages = mutableListOf<String>()
    private val slotReleaseLogMessage = slot<String>()

    @BeforeEach
    fun setUp() {
        every {
            mockLogger.debug(capture(slotDebugLogMessages))
        } just runs

        every {
            mockLogger.release(capture(slotReleaseLogMessage))
        } just runs

        every { mockInAppSkuDetailsFirst.sku } returns firstInAppId
        every { mockInAppSkuDetailsSecond.sku } returns secondInAppId
        every { mockSubsSkuDetailsFirst.sku } returns firstSubsId
        every { mockSubsSkuDetailsSecond.sku } returns secondSubsId

        dataFetcher = GoogleBillingDataFetcherImpl(mockBillingClient, mockLogger)
    }

    @Nested
    inner class LoadProductsTest {

        @Test
        fun `load products`() = runTest {
            // given
            val subsIds = listOf(firstSubsId, secondSubsId)
            val subsSkus = listOf(mockSubsSkuDetailsFirst, mockSubsSkuDetailsSecond)
            val inAppsIds = listOf(firstInAppId, secondInAppId)
            val inAppsSkus = listOf(mockInAppSkuDetailsFirst, mockInAppSkuDetailsSecond)
            val productIds = inAppsIds + subsIds
            val expectedProducts = subsSkus + inAppsSkus

            mockQuerySkuDetails(subsSkus, inAppsSkus)
            mockBillingResults()

            // when
            val result = dataFetcher.loadProducts(productIds.toSet())

            // then
            assertThat(result).isEqualTo(expectedProducts)

            verify(exactly = 2) { mockBillingClient.querySkuDetailsAsync(any(), any()) }
            assertThat(slotSkuDetailsParams[0].skusList).isEqualTo(productIds)
            assertThat(slotSkuDetailsParams[1].skusList).isEqualTo(inAppsIds)
        }

        @Test
        fun `load products only inApps`() = runTest {
            // given
            val subsIds = emptyList<String>()
            val subsSkus = emptyList<SkuDetails>()
            val inAppsIds = listOf(firstInAppId, secondInAppId)
            val inAppsSkus = listOf(mockInAppSkuDetailsFirst, mockInAppSkuDetailsSecond)
            val productIds = inAppsIds + subsIds
            val expectedProducts = subsSkus + inAppsSkus

            mockQuerySkuDetails(subsSkus, inAppsSkus)
            mockBillingResults()

            // when
            val result = dataFetcher.loadProducts(productIds.toSet())

            // then
            assertThat(result).isEqualTo(expectedProducts)

            verify(exactly = 2) { mockBillingClient.querySkuDetailsAsync(any(), any()) }
            assertThat(slotSkuDetailsParams[0].skusList).isEqualTo(productIds)
            assertThat(slotSkuDetailsParams[1].skusList).isEqualTo(inAppsIds)
        }

        @Test
        fun `load products only subs`() = runTest {
            // given
            val subsIds = listOf(firstSubsId, secondSubsId)
            val subsSkus = listOf(mockSubsSkuDetailsFirst, mockSubsSkuDetailsSecond)
            val inAppsIds = emptyList<String>()
            val inAppsSkus = emptyList<SkuDetails>()
            val productIds = inAppsIds + subsIds
            val expectedProducts = subsSkus + inAppsSkus

            mockQuerySkuDetails(subsSkus, null, inAppsThrowException = true)
            mockSubsBillingResult()

            // when
            val result = dataFetcher.loadProducts(productIds.toSet())

            // then
            assertThat(result).isEqualTo(expectedProducts)

            verify(exactly = 1) { mockBillingClient.querySkuDetailsAsync(any(), any()) }
            assertThat(slotSkuDetailsParams[0].skusList).isEqualTo(productIds)
        }
    }

    @Nested
    inner class QuerySkuDetailsTest {
        @Test
        fun `query sku details`() = runTest {
            // given
            val subsIds = listOf(firstSubsId, secondSubsId)
            val subsSkus = listOf(mockSubsSkuDetailsFirst, mockSubsSkuDetailsSecond)

            mockQuerySkuDetails(subsSkus, null, inAppsThrowException = true)
            mockSubsBillingResult()

            val skuType = BillingClient.SkuType.SUBS

            // when
            val result = dataFetcher.querySkuDetails(skuType, subsIds.toSet())

            // then
            assertThat(result).isEqualTo(subsSkus)

            verify(exactly = 1) { mockBillingClient.querySkuDetailsAsync(any(), any()) }
            assertThat(slotSkuDetailsParams.last().skuType).isEqualTo(skuType)
            assertThat(slotSkuDetailsParams.last().skusList).isEqualTo(subsIds)

            verify { mockLogger.debug(any()) }
            assertThat(slotDebugLogMessages[0])
                .startsWith("querySkuDetails() -> Querying skuDetails")
                .contains(skuType, subsIds.joinToString())
        }

        @Test
        fun `query sku details failed with error code`() {
            // given
            mockQuerySkuDetails(emptyList(), emptyList())
            mockSubsBillingResult(BillingClient.BillingResponseCode.ERROR)

            val skuType = BillingClient.SkuType.SUBS
            val ids = listOf(firstSubsId, secondSubsId)

            // when
            val exception = coAssertThatQonversionExceptionThrown(ErrorCode.SkuDetailsFetching) {
                dataFetcher.querySkuDetails(skuType, ids.toSet())
            }

            // then
            verify(exactly = 1) { mockBillingClient.querySkuDetailsAsync(any(), any()) }
            assertThat(exception.message).isEqualTo("Failed to fetch products.")
        }

        @Test
        fun `query sku details succeeded but null returned`() {
            // given
            mockQuerySkuDetails(null, emptyList())
            mockSubsBillingResult()

            val skuType = BillingClient.SkuType.SUBS
            val ids = listOf(firstSubsId, secondSubsId)

            // when
            val exception = coAssertThatQonversionExceptionThrown(ErrorCode.SkuDetailsFetching) {
                dataFetcher.querySkuDetails(skuType, ids.toSet())
            }

            // then
            verify(exactly = 1) { mockBillingClient.querySkuDetailsAsync(any(), any()) }
            assertThat(exception.message).isEqualTo("Failed to fetch products. SkuDetails list for $ids is null.")
        }
    }

    @Nested
    inner class QueryPurchasesTest {

        @Test
        fun `query purchases`() = runTest {
            // given
            val subsResult = listOf<Purchase>(mockk(), mockk())
            val inAppsResult = listOf<Purchase>(mockk(), mockk())
            val expectedResult = subsResult + inAppsResult

            mockBillingResults()
            mockQueryPurchasesAsyncForSubs(subsResult)
            mockQueryPurchasesAsyncForInApps(inAppsResult)

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
            mockBillingResults(subsResponseCode, inAppsResponseCode)
            mockQueryPurchasesAsyncForSubs(emptyList())
            mockQueryPurchasesAsyncForInApps(emptyList())

            // when
            val exception = coAssertThatQonversionExceptionThrown(ErrorCode.PurchasesFetching) {
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
            assertThat(exception.message).contains(
                subsResponseCode.getDescription(),
                inAppsResponseCode.getDescription()
            )
        }

        private fun mockQueryPurchasesAsyncForSubs(result: List<Purchase>) {
            mockQueryPurchasesAsync(BillingClient.SkuType.SUBS, mockSubsBillingResult, result)
        }

        private fun mockQueryPurchasesAsyncForInApps(result: List<Purchase>) {
            mockQueryPurchasesAsync(BillingClient.SkuType.INAPP, mockInAppsBillingResult, result)
        }

        private fun mockQueryPurchasesAsync(
            @BillingClient.SkuType skuType: String,
            billingResult: BillingResult,
            result: List<Purchase>
        ) {
            val slotPurchasesCallback = slot<PurchasesResponseListener>()
            every {
                mockBillingClient.queryPurchasesAsync(skuType, capture(slotPurchasesCallback))
            } answers {
                slotPurchasesCallback.captured.onQueryPurchasesResponse(
                    billingResult,
                    result
                )
            }
        }
    }

    @Nested
    inner class QueryAllPurchasesHistory {

        @Test
        fun `query all purchases history`() = runTest {
            // given
            val mockFirstSubsPurchaseHistoryRecord = mockPurchaseHistoryRecord("subs 1")
            val mockSecondSubsPurchaseHistoryRecord = mockPurchaseHistoryRecord("subs 2")
            val mockFirstInAppPurchaseHistoryRecord = mockPurchaseHistoryRecord("inApps 1")
            val mockSecondInAppPurchaseHistoryRecord = mockPurchaseHistoryRecord("inApps 2")

            val subsHistoryResult = listOf(mockFirstSubsPurchaseHistoryRecord, mockSecondSubsPurchaseHistoryRecord)
            val inAppsHistoryResult = listOf(mockFirstInAppPurchaseHistoryRecord, mockSecondInAppPurchaseHistoryRecord)

            val expectedResults = subsHistoryResult + inAppsHistoryResult
            val expectedSkuTypes = listOf(
                BillingClient.SkuType.SUBS,
                BillingClient.SkuType.SUBS,
                BillingClient.SkuType.INAPP,
                BillingClient.SkuType.INAPP
            )

            mockBillingResults()
            mockQueryPurchasesHistoryAsyncForSubs(subsHistoryResult)
            mockQueryPurchasesHistoryAsyncForInApps(inAppsHistoryResult)

            // when
            val result = dataFetcher.queryAllPurchasesHistory()

            // then
            verify(exactly = 1) {
                mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
                mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
            }

            assertThat(result.map { it.historyRecord }).isEqualTo(expectedResults)
            assertThat(result.map { it.type }).isEqualTo(expectedSkuTypes)
        }

        @Test
        fun `query all purchases history subs failed`() {
            val subsResponseCode = BillingClient.BillingResponseCode.ERROR
            val inAppsResponseCode = BillingClient.BillingResponseCode.OK
            testQueryAllPurchasesHistoryFailed(subsResponseCode, inAppsResponseCode)
        }

        @Test
        fun `query all purchases history inApps failed`() {
            val subsResponseCode = BillingClient.BillingResponseCode.OK
            val inAppsResponseCode = BillingClient.BillingResponseCode.ERROR
            testQueryAllPurchasesHistoryFailed(subsResponseCode, inAppsResponseCode)
        }

        @Test
        fun `query all purchases history subs and inApps failed`() {
            val subsResponseCode = BillingClient.BillingResponseCode.ERROR
            val inAppsResponseCode = BillingClient.BillingResponseCode.ERROR
            testQueryAllPurchasesHistoryFailed(subsResponseCode, inAppsResponseCode)
        }

        private fun testQueryAllPurchasesHistoryFailed(
            @BillingClient.BillingResponseCode subsResponseCode: Int,
            @BillingClient.BillingResponseCode inAppsResponseCode: Int
        ) {
            mockBillingResults(subsResponseCode, inAppsResponseCode)

            mockQueryPurchasesHistoryAsyncForSubs(emptyList())
            mockQueryPurchasesHistoryAsyncForInApps(emptyList())

            // when
            val exception = coAssertThatQonversionExceptionThrown(ErrorCode.PurchasesHistoryFetching) {
                dataFetcher.queryAllPurchasesHistory()
            }

            // then
            verify(exactly = 1) {
                mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
                mockBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
            }
            assertThat(exception.message).contains(
                subsResponseCode.getDescription(),
                inAppsResponseCode.getDescription()
            )
        }

        private fun mockQueryPurchasesHistoryAsyncForSubs(result: List<PurchaseHistoryRecord>) {
            mockQueryPurchasesHistoryAsync(BillingClient.SkuType.SUBS, mockSubsBillingResult, result)
        }

        private fun mockQueryPurchasesHistoryAsyncForInApps(result: List<PurchaseHistoryRecord>) {
            mockQueryPurchasesHistoryAsync(BillingClient.SkuType.INAPP, mockInAppsBillingResult, result)
        }

        private fun mockQueryPurchasesHistoryAsync(
            @BillingClient.SkuType skuType: String,
            billingResult: BillingResult,
            result: List<PurchaseHistoryRecord>
        ) {
            val slotPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()
            every {
                mockBillingClient.queryPurchaseHistoryAsync(skuType, capture(slotPurchasesHistoryCallback))
            } answers {
                slotPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                    billingResult,
                    result
                )
            }
        }
    }

    @Nested
    inner class QueryPurchasesHistoryTest {

        @Test
        fun `query purchases history`() = runTest {
            // given
            val skuType = BillingClient.SkuType.SUBS
            val slotPurchasesHistoryCallback = slot<PurchaseHistoryResponseListener>()
            val expectedResult = mockk<List<PurchaseHistoryRecord>>()
            every {
                mockBillingClient.queryPurchaseHistoryAsync(skuType, capture(slotPurchasesHistoryCallback))
            } answers {
                slotPurchasesHistoryCallback.captured.onPurchaseHistoryResponse(
                    mockSubsBillingResult,
                    expectedResult
                )
            }

            // when
            val result = dataFetcher.queryPurchasesHistory(skuType)

            // then
            assertThat(result.first === mockSubsBillingResult)
            assertThat(result.second === expectedResult)
            verify(exactly = 1) { mockLogger.debug("queryPurchasesHistory() -> Querying purchase history for type $skuType") }
        }
    }

    @Nested
    inner class GetHistoryFromRecordsTest {

        @Test
        fun `get history from null records for skyType SUBS`() {
            // given

            // when
            val result = dataFetcher.getHistoryFromRecords(BillingClient.SkuType.SUBS, null)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { mockLogger.debug(any()) }
        }

        @Test
        fun `get history from null records for skyType INAPP`() {
            // given

            // when
            val result = dataFetcher.getHistoryFromRecords(BillingClient.SkuType.INAPP, null)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { mockLogger.debug(any()) }
        }

        @Test
        fun `get history from empty records for skyType SUBS`() {
            // given

            // when
            val result = dataFetcher.getHistoryFromRecords(BillingClient.SkuType.SUBS, emptyList())

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { mockLogger.debug(any()) }
        }

        @Test
        fun `get history from empty records for skyType INAPP`() {
            // given

            // when
            val result = dataFetcher.getHistoryFromRecords(BillingClient.SkuType.INAPP, emptyList())

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { mockLogger.debug(any()) }
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
            val mockFirstHistoryRecord = mockPurchaseHistoryRecord("1")
            val mockSecondHistoryRecord = mockPurchaseHistoryRecord("2")
            val historyRecords = listOf(mockFirstHistoryRecord, mockSecondHistoryRecord)

            // when
            val result = dataFetcher.getHistoryFromRecords(skuType, historyRecords)

            // then
            verify(exactly = historyRecords.size) { mockLogger.debug(any()) }
            slotDebugLogMessages.forEach {
                assertThat(it).startsWith("queryAllPurchasesHistory() -> purchase history")
            }
            assertThat(slotDebugLogMessages[0]).contains(mockFirstHistoryRecord.getDescription())
            assertThat(slotDebugLogMessages[1]).contains(mockSecondHistoryRecord.getDescription())

            assertThat(result.map { it.historyRecord }).isEqualTo(historyRecords)
            result.forEach { assertThat(it.type).isEqualTo(skuType) }
        }
    }

    @Nested
    inner class LogSkuDetailsTest {

        @Test
        fun `log sku details empty list`() {
            // given
            val skuList = listOf(firstSubsId, firstInAppId)

            // when
            dataFetcher.logSkuDetails(emptyList(), skuList.toSet())

            // then
            verify(exactly = 1) {
                mockLogger.release("querySkuDetails() -> SkuDetails list for $skuList is empty.")
            }
        }

        @Test
        fun `log sku details not empty list`() {
            // given
            val skuList = listOf(firstSubsId, firstInAppId)
            val skuDetails = listOf(mockSubsSkuDetailsFirst, mockInAppSkuDetailsFirst)

            // when
            dataFetcher.logSkuDetails(skuDetails, skuList.toSet())

            // then
            skuDetails.forEach {
                verify(exactly = 1) {
                    mockLogger.debug("querySkuDetails() -> $it")
                }
            }
        }
    }

    private fun mockQuerySkuDetails(
        subsResult: List<SkuDetails>?,
        inAppsResult: List<SkuDetails>?,
        subsThrowException: Boolean = false,
        inAppsThrowException: Boolean = false,
    ) {
        every {
            mockBillingClient.querySkuDetailsAsync(capture(slotSkuDetailsParams), capture(slotQuerySkuDetailsCallback))
        } answers {
            when (slotSkuDetailsParams.last().skuType) {
                BillingClient.SkuType.SUBS -> {
                    if (subsThrowException) throw IllegalStateException("No result for requested SKU type")
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockSubsBillingResult,
                        subsResult
                    )
                }
                BillingClient.SkuType.INAPP -> {
                    if (inAppsThrowException) throw IllegalStateException("No result for requested SKU type")
                    slotQuerySkuDetailsCallback.captured.onSkuDetailsResponse(
                        mockInAppsBillingResult,
                        inAppsResult
                    )
                }
                else -> {
                    throw IllegalStateException("Unexpected SKU type")
                }
            }
        }
    }

    private fun mockSubsBillingResult(
        @BillingClient.BillingResponseCode code: Int = BillingClient.BillingResponseCode.OK
    ) = mockBillingResult(mockSubsBillingResult, code)

    private fun mockInAppsBillingResult(
        @BillingClient.BillingResponseCode code: Int = BillingClient.BillingResponseCode.OK
    ) = mockBillingResult(mockInAppsBillingResult, code)

    private fun mockBillingResults(
        @BillingClient.BillingResponseCode subsCode: Int = BillingClient.BillingResponseCode.OK,
        @BillingClient.BillingResponseCode inAppsCode: Int = BillingClient.BillingResponseCode.OK
    ) {
        mockBillingResult(mockSubsBillingResult, subsCode)
        mockBillingResult(mockInAppsBillingResult, inAppsCode)
    }

    private fun mockBillingResult(billingResult: BillingResult, @BillingClient.BillingResponseCode code: Int) {
        every { billingResult.responseCode } returns code
    }

    private fun mockPurchaseHistoryRecord(sku: String) = mockk<PurchaseHistoryRecord>(relaxed = true).also {
        every { it.skus } returns arrayListOf(sku)
    }
}
