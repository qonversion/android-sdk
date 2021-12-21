package com.qonversion.android.sdk.internal.billing.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dto.BillingError
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.clearMocks
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

@ExperimentalCoroutinesApi
internal class GoogleBillingControllerTest {

    private lateinit var billingController: GoogleBillingControllerImpl

    private val mockConsumer = mockk<GoogleBillingConsumer>()
    private val mockPurchaser = mockk<GoogleBillingPurchaser>()
    private val mockDataFetcher = mockk<GoogleBillingDataFetcher>()
    private val mockPurchaseListener = mockk<PurchasesListener>()
    private val mockBillingClient = mockk<BillingClient>()
    private val mockLogger = mockk<Logger>(relaxed = true)

    private val awaitTimeoutMs = 100L

    @BeforeEach
    fun setUp() {
        every {
            mockBillingClient.isReady
        } returns true

        billingController = GoogleBillingControllerImpl(
            mockConsumer,
            mockPurchaser,
            mockDataFetcher,
            mockPurchaseListener,
            mockLogger
        )
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
        private val mockBillingClientStateListener = mockk<BillingClientStateListener>()

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
        fun `client is already ready`() = runTest {
            // given
            every { mockBillingClient.isReady } returns true

            // when
            val error = withTimeout(waitingEpsilonMs) {
                billingController.waitForReadyClient()
            }

            // then
            assertThat(error).isNull()
        }

        @Test
        fun `client is not ready and connects immediately`() = runTest {
            // given
            every { mockBillingClient.isReady } returns false
            billingController.connectionDeferred = CompletableDeferred()

            // when
            val error = withTimeout(waitingEpsilonMs) {
                launch {
                    billingController.connectionDeferred?.complete(null)
                }
                billingController.waitForReadyClient()
            }

            // then
            assertThat(error).isNull()
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
        fun `client is not ready and connects with error`() = runTest {
            // given
            every { mockBillingClient.isReady } returns false
            billingController.connectionDeferred = CompletableDeferred()
            val billingError = mockk<BillingError>()

            // when
            val error = withTimeout(waitingEpsilonMs) {
                launch {
                    billingController.connectionDeferred?.complete(billingError)
                }
                billingController.waitForReadyClient()
            }

            // then
            assertThat(error === billingError)
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