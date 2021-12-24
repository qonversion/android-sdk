package com.qonversion.android.sdk.internal.billing.controller

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dto.BillingError
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.Exception

@ExperimentalCoroutinesApi
internal class GoogleBillingControllerTest {

    private lateinit var billingController: GoogleBillingControllerImpl

    private val mockConsumer = mockk<GoogleBillingConsumer>()
    private val mockPurchaser = mockk<GoogleBillingPurchaser>()
    private val mockDataFetcher = mockk<GoogleBillingDataFetcher>()
    private val mockPurchasesListener = mockk<PurchasesListener>()
    private val mockBillingClient = mockk<BillingClient>()
    private val mockLogger = mockk<Logger>(relaxed = true)

    private val awaitTimeoutMs = 100L

    @BeforeEach
    fun setUp() {
        billingController = GoogleBillingControllerImpl(
            mockConsumer,
            mockPurchaser,
            mockDataFetcher,
            mockPurchasesListener,
            mockLogger
        )

        every { mockConsumer.setup(mockBillingClient) } just runs
        every { mockDataFetcher.setup(mockBillingClient) } just runs
        every { mockPurchaser.setup(mockBillingClient) } just runs
    }

    @Nested
    inner class CompleteConnectionDeferredTest {
        @BeforeEach
        fun setUp() {
            every {
                mockBillingClient.startConnection(any())
            } just runs
            billingController.billingClient = mockBillingClient
        }

        @Test
        fun `complete connection deferred`() = runTest {
            // given
            val deferred = CompletableDeferred<BillingError?>()
            billingController.connectionDeferred = deferred

            // when
            billingController.completeConnectionDeferred()
            val result = withTimeout(awaitTimeoutMs) {
                deferred.await()
            }

            // then
            assertThat(result).isNull()
            assertThat(billingController.connectionDeferred).isNull()
        }

        @Test
        fun `complete connection deferred with error`() = runTest {
            // given
            val error = mockk<BillingError>()
            val deferred = CompletableDeferred<BillingError?>()
            billingController.connectionDeferred = deferred

            // when
            billingController.completeConnectionDeferred(error)
            val result = withTimeout(awaitTimeoutMs) {
                deferred.await()
            }

            // then
            assertThat(result).isEqualTo(error)
            assertThat(billingController.connectionDeferred).isNull()
        }

        @Test
        fun `complete null connection deferred`() = runTest {
            // given
            billingController.connectionDeferred = null

            // when
            assertDoesNotThrow {
                billingController.completeConnectionDeferred()
            }

            // then
            assertThat(billingController.connectionDeferred).isNull()
        }
    }

    @Nested
    inner class ConnectToGoogleBillingTest {

        @BeforeEach
        fun setUp() {
            every {
                mockBillingClient.startConnection(any())
            } just runs
        }

        @Test
        fun `connect to billing after setting client`() {
            // given

            // when
            billingController.billingClient = mockBillingClient

            // then
            verify(exactly = 1) {
                mockBillingClient.startConnection(billingController.billingClientStateListener)
            }
            assertThat(billingController.connectionDeferred).isNotNull
        }

        @Test
        fun `connect to billing again while another connection is in progress`() = runTest {
            // given
            billingController.billingClientStateListener = mockk(relaxed = true)
            mockConnection(timeout = 100L)
            billingController.billingClient = mockBillingClient // launches connection
            val currentDeferred = billingController.connectionDeferred

            // when
            val connectionDeferred = billingController.connectToBillingAsync()

            // then
            verify(exactly = 1) {
                mockBillingClient.startConnection(billingController.billingClientStateListener)
            }
            assertThat(currentDeferred === connectionDeferred)
        }

        @Test
        fun `connect to billing while billing client is null`() {
            // given

            // when and then
            @Suppress("DeferredResultUnused")
            assertThatQonversionExceptionThrown(ErrorCode.BillingConnection) {
                billingController.connectToBillingAsync()
            }
        }
    }

    @Nested
    inner class WaitForReadyClientTest {

        private val waitingEpsilonMs = 10L

        @BeforeEach
        fun setUp() {
            every {
                mockBillingClient.startConnection(any())
            } just runs
            billingController.billingClient = mockBillingClient
        }

        @Test
        fun `client is already ready`() {
            // given
            every { mockBillingClient.isReady } returns true

            // when and then
            assertDoesNotThrow {
                runTest {
                    withTimeout(waitingEpsilonMs) {
                        billingController.waitForReadyClient()
                    }
                }
            }
        }

        @Test
        fun `client is not ready and connects immediately`() {
            // given
            every { mockBillingClient.isReady } returns false
            billingController.connectionDeferred = CompletableDeferred()

            // when and then
            assertDoesNotThrow {
                runTest {
                    withTimeout(waitingEpsilonMs) {
                        launch {
                            billingController.connectionDeferred?.complete(null)
                        }
                        billingController.waitForReadyClient()
                    }
                }
            }
        }

        @Test
        fun `client is not ready and connects with delay`() {
            // given
            every { mockBillingClient.isReady } returns false
            billingController.connectionDeferred = CompletableDeferred()
            val delay = 100000L

            // when and then
            // assert that it really waits for the whole connection time
            assertThatThrownBy {
                runTest {
                    withTimeout(delay - 1) {
                        launch {
                            delay(delay)
                            billingController.connectionDeferred?.complete(null)
                        }
                        billingController.waitForReadyClient()
                    }
                }
            }.isInstanceOf(TimeoutCancellationException::class.java)
        }

        @Test
        fun `client is not ready and connects with error`() {
            // given
            every { mockBillingClient.isReady } returns false
            billingController.connectionDeferred = CompletableDeferred()
            val billingError = mockk<BillingError>()
            val exception = QonversionException(ErrorCode.PlayStore)
            every { billingError.toQonversionException() } returns exception

            // when
            val e = coAssertThatQonversionExceptionThrown(ErrorCode.PlayStore) {
                withTimeout(waitingEpsilonMs) {
                    launch {
                        billingController.connectionDeferred?.complete(billingError)
                    }
                    billingController.waitForReadyClient()
                }
            }

            // then
            assertThat(e === exception)
        }
    }

    @Nested
    inner class BillingClientStateListenerTest {
        private val slotDebugLogMessage = slot<String>()
        private val slotReleaseLogMessage = slot<String>()
        private val mockBillingResult = mockk<BillingResult>()

        @BeforeEach
        fun setUp() {
            every {
                mockBillingClient.startConnection(any())
            } just runs
            billingController.billingClient = mockBillingClient

            clearMocks(mockLogger) // clear from messages written by billing client setter
            every { mockLogger.debug(capture(slotDebugLogMessage)) } just runs
            every { mockLogger.release(capture(slotReleaseLogMessage)) } just runs
        }

        @Test
        fun `on disconnected`() {
            // given

            // when
            billingController.billingClientStateListener.onBillingServiceDisconnected()

            // then
            verify { mockLogger.debug(any()) }
            assertThat(slotDebugLogMessage.captured)
                .startsWith("billingClientStateListener -> BillingClient disconnected")
        }

        @Test
        fun `on connected with OK response`() {
            // given
            every { mockBillingResult.responseCode } returns BillingClient.BillingResponseCode.OK
            billingController.connectionDeferred = mockk(relaxed = true)

            // when
            billingController.billingClientStateListener.onBillingSetupFinished(mockBillingResult)

            // then
            verify { mockLogger.debug(any()) }
            assertThat(slotDebugLogMessage.captured)
                .startsWith("billingClientStateListener -> BillingClient successfully connected")
            assertThat(billingController.connectionDeferred).isNull()
        }

        @Test
        fun `on connection failed with billing unavailable error`() {
            testOnConnectionFailedWithNonRetryableError(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        }

        @Test
        fun `on connection failed with feature not supported response`() {
            testOnConnectionFailedWithNonRetryableError(BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED)
        }

        @Test
        fun `on connection failed because another connection is in process`() {
            // given
            every { mockBillingResult.responseCode } returns BillingClient.BillingResponseCode.DEVELOPER_ERROR

            // when
            billingController.billingClientStateListener.onBillingSetupFinished(mockBillingResult)

            // then
            verify(exactly = 0) {
                mockLogger.debug(any())
                mockLogger.release(any())
            }
        }

        @Test
        fun `on connection failed with temporary error`() {
            // given
            every { mockBillingResult.responseCode } returns BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
            billingController.connectionDeferred = mockk(relaxed = true)

            // when
            billingController.billingClientStateListener.onBillingSetupFinished(mockBillingResult)

            // then
            verify { mockLogger.release(any()) }
            assertThat(slotReleaseLogMessage.captured)
                .startsWith("billingClientStateListener -> BillingClient connection failed with error: ")
            assertThat(slotReleaseLogMessage.captured).contains("SERVICE_UNAVAILABLE")
            assertThat(billingController.connectionDeferred).isNotNull
        }

        private fun testOnConnectionFailedWithNonRetryableError(
            @BillingClient.BillingResponseCode responseCode: Int
        ) {
            // given
            every { mockBillingResult.responseCode } returns responseCode
            val deferred = mockk<CompletableDeferred<BillingError?>>(relaxed = true)
            val slotBillingError = slot<BillingError>()
            every { deferred.complete(capture(slotBillingError)) } returns true
            billingController.connectionDeferred = deferred

            // when
            billingController.billingClientStateListener.onBillingSetupFinished(mockBillingResult)

            // then
            verify { mockLogger.release(any()) }
            assertThat(slotReleaseLogMessage.captured)
                .startsWith("billingClientStateListener -> BillingClient connection failed with error")
            assertThat(slotReleaseLogMessage.captured).contains(responseCode.getDescription())

            assertThat(billingController.connectionDeferred).isNull()
            verify(exactly = 1) { deferred.complete(any()) }
            assertThat(slotBillingError.captured.billingResponseCode).isEqualTo(responseCode)
        }
    }

    @Nested
    inner class PurchasesUpdatedListenerTest {

        private val mockBillingResult = mockk<BillingResult>()
        private val mockPurchase1 = mockk<Purchase>(relaxed = true)
        private val mockPurchase2 = mockk<Purchase>(relaxed = true)
        private val purchase1Sku = "purchase 1 sku"
        private val purchase2Sku = "purchase 2 sku"
        private val slotBillingError = slot<BillingError>()
        private val slotDebugLogMessage = slot<String>()
        private val slotReleaseLogMessages = mutableListOf<String>()

        @BeforeEach
        fun setUp() {
            every {
                mockPurchasesListener.onPurchasesFailed(any(), capture(slotBillingError))
            } just runs
            every { mockPurchasesListener.onPurchasesCompleted(any()) } just runs

            every { mockLogger.debug(capture(slotDebugLogMessage)) } just runs
            every { mockLogger.release(capture(slotReleaseLogMessages)) } just runs

            every { mockPurchase1.skus } returns arrayListOf(purchase1Sku)
            every { mockPurchase2.skus } returns arrayListOf(purchase2Sku)

            billingController.purchasesUpdatedListener
        }

        @Test
        fun `successful update`() {
            // given
            every { mockBillingResult.responseCode } returns BillingClient.BillingResponseCode.OK
            val purchases = listOf(mockPurchase1, mockPurchase2)

            // when
            billingController.purchasesUpdatedListener.onPurchasesUpdated(mockBillingResult, purchases)

            // then
            verify(exactly = 1) { mockPurchasesListener.onPurchasesCompleted(purchases) }
            verify { mockLogger.debug(any()) }
            assertThat(slotDebugLogMessage.captured).startsWith("onPurchasesUpdated() -> purchases updated.")
        }

        @Test
        fun `successful update with null purchases`() {
            // given
            val responseCode = BillingClient.BillingResponseCode.OK
            every { mockBillingResult.responseCode } returns responseCode
            val purchases = null
            val expectedErrorMessage = "No purchase was passed for successful billing result."

            // when
            billingController.purchasesUpdatedListener.onPurchasesUpdated(mockBillingResult, purchases)

            // then
            verify(exactly = 1) { mockPurchasesListener.onPurchasesFailed(emptyList(), any()) }
            assertThat(slotBillingError.captured.billingResponseCode).isEqualTo(responseCode)
            assertThat(slotBillingError.captured.message).isEqualTo(expectedErrorMessage)

            verify(exactly = 1) { mockLogger.release(any()) } // once for error and once for purchases
            assertThat(slotReleaseLogMessages[0])
                .startsWith("onPurchasesUpdated() -> failed to update purchases")
                .contains(expectedErrorMessage)
        }

        @Test
        fun `failed update`() {
            // given
            val errorCode = BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
            every { mockBillingResult.responseCode } returns errorCode
            val purchases = listOf(mockPurchase1, mockPurchase2)

            // when
            billingController.purchasesUpdatedListener.onPurchasesUpdated(mockBillingResult, purchases)

            // then
            verify(exactly = 1) { mockPurchasesListener.onPurchasesFailed(purchases, any()) }
            assertThat(slotBillingError.captured.billingResponseCode).isEqualTo(errorCode)
            assertThat(slotBillingError.captured.message).contains("ITEM_ALREADY_OWNED")

            verify(exactly = 2) { mockLogger.release(any()) } // once for error and once for purchases
            assertThat(slotReleaseLogMessages[0])
                .startsWith("onPurchasesUpdated() -> failed to update purchases")
                .contains("ITEM_ALREADY_OWNED")
            assertThat(slotReleaseLogMessages[1])
                .startsWith("Purchases:")
                .contains(purchase1Sku, purchase2Sku)
        }
    }

    @Nested
    inner class QueryPurchasesHistoryTest {

        @Test
        fun `billing successfully connected`() = runTest {
            // given
            val mockPurchasesHistory = mockk<List<PurchaseHistory>>()
            coEvery { mockDataFetcher.queryAllPurchasesHistory() } returns mockPurchasesHistory
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            // when
            val result = billingController.queryPurchasesHistory()

            // then
            coVerify(exactly = 1) { mockDataFetcher.queryAllPurchasesHistory() }
            assertThat(result === mockPurchasesHistory)
        }

        @Test
        fun `billing connection failed with error`() {
            // given
            val billingResult = mockk<BillingResult>().apply {
                every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
            }
            mockConnection(billingResult)
            every { mockBillingClient.isReady } returns false
            billingController.billingClient = mockBillingClient

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.BillingUnavailable) {
                billingController.queryPurchasesHistory()
            }

            // then
            coVerify(exactly = 0) { mockDataFetcher.queryAllPurchasesHistory() }
        }

        @Test
        fun `data fetcher throws error`() {
            // given
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val exception = mockk<QonversionException>()
            coEvery { mockDataFetcher.queryAllPurchasesHistory() } throws exception

            // when
            val e = try {
                runTest {
                    billingController.queryPurchasesHistory()
                }
                null
            } catch (e: Exception) {
                e
            }

            // then
            coVerify(exactly = 1) { mockDataFetcher.queryAllPurchasesHistory() }
            assertThat(e === exception)
        }
    }

    @Nested
    inner class QueryPurchasesTest {

        @Test
        fun `billing successfully connected`() = runTest {
            // given
            val mockPurchases = mockk<List<Purchase>>()
            coEvery { mockDataFetcher.queryPurchases() } returns mockPurchases
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            // when
            val result = billingController.queryPurchases()

            // then
            coVerify(exactly = 1) { mockDataFetcher.queryPurchases() }
            assertThat(result === mockPurchases)
        }

        @Test
        fun `billing connection failed with error`() {
            // given
            val billingResult = mockk<BillingResult>().apply {
                every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
            }
            mockConnection(billingResult)
            every { mockBillingClient.isReady } returns false
            billingController.billingClient = mockBillingClient

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.BillingUnavailable) {
                billingController.queryPurchases()
            }

            // then
            coVerify(exactly = 0) { mockDataFetcher.queryPurchases() }
        }

        @Test
        fun `data fetcher throws error`() {
            // given
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val exception = mockk<QonversionException>()
            coEvery { mockDataFetcher.queryPurchases() } throws exception

            // when
            val e = try {
                runTest {
                    billingController.queryPurchases()
                }
                null
            } catch (e: Exception) {
                e
            }

            // then
            coVerify(exactly = 1) { mockDataFetcher.queryPurchases() }
            assertThat(e === exception)
        }
    }

    @Nested
    inner class LoadProductsTest {

        @Test
        fun `billing successfully connected`() = runTest {
            // given
            val mockSkuDetails = mockk<List<SkuDetails>>()
            val ids = setOf("1")
            coEvery { mockDataFetcher.loadProducts(ids) } returns mockSkuDetails
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            // when
            val result = billingController.loadProducts(ids)

            // then
            coVerify(exactly = 1) { mockDataFetcher.loadProducts(ids) }
            assertThat(result === mockSkuDetails)
        }

        @Test
        fun `empty input set`() = runTest {
            // given
            val ids = emptySet<String>()

            // when
            val result = billingController.loadProducts(ids)

            // then
            coVerify(exactly = 0) { mockDataFetcher.loadProducts(any()) }
            assertThat(result).isEmpty()
        }

        @Test
        fun `billing connection failed with error`() {
            // given
            val billingResult = mockk<BillingResult>().apply {
                every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
            }
            mockConnection(billingResult)
            every { mockBillingClient.isReady } returns false
            billingController.billingClient = mockBillingClient

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.BillingUnavailable) {
                billingController.loadProducts(setOf("1"))
            }

            // then
            coVerify(exactly = 0) { mockDataFetcher.queryPurchases() }
        }

        @Test
        fun `data fetcher throws error`() {
            // given
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val exception = mockk<QonversionException>()
            coEvery { mockDataFetcher.loadProducts(any()) } throws exception

            // when
            val e = try {
                runTest {
                    billingController.loadProducts(setOf("1"))
                }
                null
            } catch (e: Exception) {
                e
            }

            // then
            coVerify(exactly = 1) { mockDataFetcher.loadProducts(any()) }
            assertThat(e === exception)
        }
    }

    @Nested
    inner class GetSkuDetailsFromPurchasesTest {

        private val purchase1Sku = "purchase 1 sku"
        private val purchase2Sku = "purchase 2 sku"
        private val purchase1 = mockk<Purchase>()
        private val purchase2 = mockk<Purchase>()

        @BeforeEach
        fun setUp() {
            every { purchase1.skus } returns arrayListOf(purchase1Sku)
            every { purchase2.skus } returns arrayListOf(purchase2Sku)
        }

        @Test
        fun `several purchases`() = runTest {
            // given
            val mockSkuDetails = mockk<List<SkuDetails>>()
            val slotIds = slot<Set<String>>()
            coEvery { mockDataFetcher.loadProducts(capture(slotIds)) } answers { mockSkuDetails }
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient
            val purchases = listOf(purchase1, purchase2)

            // when
            val result = billingController.getSkuDetailsFromPurchases(purchases)

            // then
            coVerify(exactly = 1) { mockDataFetcher.loadProducts(any()) }
            assertThat(slotIds.captured).isEqualTo(setOf(purchase1Sku, purchase2Sku))
            assertThat(result === mockSkuDetails)
        }

        @Test
        fun `no purchases`() = runTest {
            // given
            val purchases = emptyList<Purchase>()

            // when
            val result = billingController.getSkuDetailsFromPurchases(purchases)

            // then
            coVerify(exactly = 0) { mockDataFetcher.loadProducts(any()) }
            assertThat(result).isEmpty()
        }

        @Test
        fun `all purchases without sku`() = runTest {
            // given
            val purchases = List(4) {
                mockk<Purchase>().also {
                    every { it.skus } returns arrayListOf(null)
                }
            }

            // when
            val result = billingController.getSkuDetailsFromPurchases(purchases)

            // then
            coVerify(exactly = 0) { mockDataFetcher.loadProducts(any()) }
            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class ConsumeTest {

        private val consumingToken = "test token"

        @Test
        fun `billing successfully connected`() {
            // given
            coEvery { mockConsumer.consume(consumingToken) } just runs
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            // when
            runTest {
                billingController.consume(consumingToken)
            }

            // then
            coVerify(exactly = 1) { mockConsumer.consume(consumingToken) }
        }

        @Test
        fun `consumer throws exception`() {
            // given
            val exception = QonversionException(ErrorCode.Consuming)
            coEvery { mockConsumer.consume(consumingToken) } throws exception
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val slotReleaseMessage = slot<String>()
            every { mockLogger.release(capture(slotReleaseMessage)) } just runs

            // when
            assertDoesNotThrow {
                runTest {
                    billingController.consume(consumingToken)
                }
            }

            // then
            coVerify(exactly = 1) { mockConsumer.consume(consumingToken) }
            verify(exactly = 1) { mockLogger.release(any()) }
            assertThat(slotReleaseMessage.captured)
                .startsWith("Failed to consume purchase")
                .contains(consumingToken)
        }

        @Test
        fun `billing connection failed with error`() {
            // given
            val billingResult = mockk<BillingResult>().apply {
                every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
            }
            mockConnection(billingResult)
            every { mockBillingClient.isReady } returns false
            billingController.billingClient = mockBillingClient

            // when
            assertDoesNotThrow {
                runTest {
                    billingController.consume(consumingToken)
                }
            }

            // then
            coVerify(exactly = 0) { mockConsumer.consume(any()) }
        }
    }

    @Nested
    inner class AcknowledgeTest {

        private val acknowledgingToken = "test token"

        @Test
        fun `billing successfully connected`() {
            // given
            coEvery { mockConsumer.acknowledge(acknowledgingToken) } just runs
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            // when
            runTest {
                billingController.acknowledge(acknowledgingToken)
            }

            // then
            coVerify(exactly = 1) { mockConsumer.acknowledge(acknowledgingToken) }
        }

        @Test
        fun `consumer throws exception`() {
            // given
            val exception = QonversionException(ErrorCode.Consuming)
            coEvery { mockConsumer.acknowledge(acknowledgingToken) } throws exception
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val slotReleaseMessage = slot<String>()
            every { mockLogger.release(capture(slotReleaseMessage)) } just runs

            // when
            assertDoesNotThrow {
                runTest {
                    billingController.acknowledge(acknowledgingToken)
                }
            }

            // then
            coVerify(exactly = 1) { mockConsumer.acknowledge(acknowledgingToken) }
            verify(exactly = 1) { mockLogger.release(any()) }
            assertThat(slotReleaseMessage.captured)
                .startsWith("Failed to acknowledge purchase")
                .contains(acknowledgingToken)
        }

        @Test
        fun `billing connection failed with error`() {
            // given
            val billingResult = mockk<BillingResult>().apply {
                every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
            }
            mockConnection(billingResult)
            every { mockBillingClient.isReady } returns false
            billingController.billingClient = mockBillingClient

            // when
            assertDoesNotThrow {
                runTest {
                    billingController.acknowledge(acknowledgingToken)
                }
            }

            // then
            coVerify(exactly = 0) { mockConsumer.acknowledge(any()) }
        }
    }

    @Nested
    inner class PurchaseTest {

        private val mockActivity = mockk<Activity>()
        private val skuType = BillingClient.SkuType.SUBS
        private val mockSkuDetails = mockk<SkuDetails>()
        private val slotUpdatePurchaseInfo = slot<UpdatePurchaseInfo>()

        @BeforeEach
        fun setUp() {
            coEvery {
                mockPurchaser.purchase(mockActivity, mockSkuDetails, capture(slotUpdatePurchaseInfo))
            } just runs

            every { mockSkuDetails.sku } returns "1"
            every { mockSkuDetails.type } returns skuType
        }

        @Test
        fun `billing successfully connected`() {
            // given
            coEvery { mockPurchaser.purchase(mockActivity, mockSkuDetails) } just runs
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            // when
            runTest {
                billingController.purchase(mockActivity, mockSkuDetails)
            }

            // then
            coVerify(exactly = 1) { mockPurchaser.purchase(mockActivity, mockSkuDetails) }
        }

        @Test
        fun `billing connection failed with error`() {
            // given
            val billingResult = mockk<BillingResult>().apply {
                every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
            }
            mockConnection(billingResult)
            every { mockBillingClient.isReady } returns false
            billingController.billingClient = mockBillingClient

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.BillingUnavailable) {
                billingController.purchase(mockActivity, mockSkuDetails)
            }

            // then
            coVerify(exactly = 0) { mockPurchaser.purchase(any(), any(), any()) }
        }

        @Test
        fun `purchase update success`() {
            // given
            val sku = "temp sku"
            val mockOldSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns sku
            }
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val testPurchaseToken = "test purchase token"
            val mockPurchaseHistoryRecord = mockk<PurchaseHistoryRecord>().also {
                every { it.purchaseToken } returns testPurchaseToken
                every { it.skus } returns arrayListOf(sku)
            }
            val mockBillingResult = mockk<BillingResult>().also {
                every { it.responseCode } returns BillingClient.BillingResponseCode.OK
            }
            coEvery {
                mockDataFetcher.queryPurchasesHistory(skuType)
            } returns Pair(mockBillingResult, listOf(mockPurchaseHistoryRecord))

            // when
            runTest {
                billingController.purchase(mockActivity, mockSkuDetails, mockOldSkuDetails)
            }

            // then
            coVerify(exactly = 1) { mockPurchaser.purchase(mockActivity, mockSkuDetails, any()) }
            assertThat(slotUpdatePurchaseInfo.captured.purchaseToken).isEqualTo(testPurchaseToken)
        }

        @Test
        fun `purchase update - failed to query history`() {
            // given
            val sku = "temp sku"
            val mockOldSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns sku
            }
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val mockBillingResult = mockk<BillingResult>().also {
                every { it.responseCode } returns BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            }
            coEvery {
                mockDataFetcher.queryPurchasesHistory(skuType)
            } returns Pair(mockBillingResult, emptyList())

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.ProductUnavailable) {
                billingController.purchase(mockActivity, mockSkuDetails, mockOldSkuDetails)
            }

            // then
            coVerify(exactly = 0) { mockPurchaser.purchase(mockActivity, mockSkuDetails, any()) }
        }

        @Test
        fun `purchase update - query history returned null`() {
            // given
            val sku = "temp sku"
            val mockOldSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns sku
            }
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val mockBillingResult = mockk<BillingResult>().also {
                every { it.responseCode } returns BillingClient.BillingResponseCode.OK
            }
            coEvery {
                mockDataFetcher.queryPurchasesHistory(skuType)
            } returns Pair(mockBillingResult, null)

            // when
            coAssertThatQonversionExceptionThrown {
                billingController.purchase(mockActivity, mockSkuDetails, mockOldSkuDetails)
            }

            // then
            coVerify(exactly = 0) { mockPurchaser.purchase(mockActivity, mockSkuDetails, any()) }
        }

        @Test
        fun `purchase update - history with sku not found`() {
            // given
            val sku = "temp sku"
            val mockOldSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns sku
            }
            mockConnection()
            every { mockBillingClient.isReady } returns true
            billingController.billingClient = mockBillingClient

            val mockBillingResult = mockk<BillingResult>().also {
                every { it.responseCode } returns BillingClient.BillingResponseCode.OK
            }
            coEvery {
                mockDataFetcher.queryPurchasesHistory(skuType)
            } returns Pair(mockBillingResult, emptyList())

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.Purchasing) {
                billingController.purchase(mockActivity, mockSkuDetails, mockOldSkuDetails)
            }

            // then
            coVerify(exactly = 0) { mockPurchaser.purchase(mockActivity, mockSkuDetails, any()) }
        }
    }

    private fun mockConnection(result: BillingResult? = null, timeout: Long = 0L) {
        val actualResult = result ?: mockk<BillingResult>().apply {
            every { responseCode } returns BillingClient.BillingResponseCode.OK
        }
        val slotListener = slot<BillingClientStateListener>()
        every {
            mockBillingClient.startConnection(capture(slotListener))
        } answers {
            runTest { delay(timeout) }
            slotListener.captured.onBillingSetupFinished(actualResult)
        }
    }
}