package com.qonversion.android.sdk.internal.billing.consumer

import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeResponseListener
import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertDoesNotThrow

internal class GoogleBillingConsumerTest {

    private lateinit var googleBillingConsumer: GoogleBillingConsumer
    private val logger = mockk<Logger>()
    private val billingClient = mockk<BillingClient>()
    private val purchaseToken = "test"
    private val consumeCallback = slot<ConsumeResponseListener>()
    private val acknowledgeCallback = slot<AcknowledgePurchaseResponseListener>()
    private val billingResult = mockk<BillingResult>()

    @Before
    fun setUp() {
        googleBillingConsumer = GoogleBillingConsumerImpl(logger)
        googleBillingConsumer.setup(billingClient)

        every {
            logger.verbose(any())
        } just runs
        every {
            billingClient.consumeAsync(any(), capture(consumeCallback))
        } answers {
            consumeCallback.captured.onConsumeResponse(billingResult, purchaseToken)
        }
        every {
            billingClient.acknowledgePurchase(any(), capture(acknowledgeCallback))
        } answers {
            acknowledgeCallback.captured.onAcknowledgePurchaseResponse(billingResult)
        }
    }

    @Test
    fun `consuming success`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        // when
        assertDoesNotThrow {
            googleBillingConsumer.consume(purchaseToken)
        }

        // then
        verify { billingClient.consumeAsync(any(), any()) }
    }

    @Test
    fun `consuming error`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        // when
        val exception = assertThatQonversionExceptionThrown(ErrorCode.Consuming) {
            googleBillingConsumer.consume(purchaseToken)
        }

        // then
        verify { billingClient.consumeAsync(any(), any()) }
        assertThat(exception.message).contains(purchaseToken, "ERROR")
    }

    @Test
    fun `acknowledging success`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        // when
        assertDoesNotThrow {
            googleBillingConsumer.acknowledge(purchaseToken)
        }

        // then
        verify { billingClient.acknowledgePurchase(any(), any()) }
    }

    @Test
    fun `acknowledging error`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        // when
        val exception = assertThatQonversionExceptionThrown(ErrorCode.Acknowledging) {
            googleBillingConsumer.acknowledge(purchaseToken)
        }

        // then
        verify { billingClient.acknowledgePurchase(any(), any()) }
        assertThat(exception.message).contains(purchaseToken, "ERROR")
    }
}
