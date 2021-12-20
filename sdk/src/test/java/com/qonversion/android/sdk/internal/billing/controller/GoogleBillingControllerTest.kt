package com.qonversion.android.sdk.internal.billing.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dto.BillingError
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.old.mockPrivateField
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
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

    private fun mockConnection(result: BillingResult = mockk(relaxed = true), timeout: Long = 0L) {
        val slotListener = slot<BillingClientStateListener>()
        every {
            mockBillingClient.startConnection(capture(slotListener))
        } answers {
            Thread.sleep(timeout)
            slotListener.captured.onBillingSetupFinished(result)
        }
    }
}