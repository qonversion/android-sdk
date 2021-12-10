package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeResponseListener
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import java.lang.Exception

internal class GoogleBillingConsumerTest {

    private lateinit var googleBillingConsumer: GoogleBillingConsumer
    private val billingClient = mockk<BillingClient>()
    private val purchaseToken = "test"
    private val consumeCallback = slot<ConsumeResponseListener>()
    private val acknowledgeCallback = slot<AcknowledgePurchaseResponseListener>()
    private val billingResult = mockk<BillingResult>()

    @Before
    fun setUp() {
        googleBillingConsumer = GoogleBillingConsumerImpl(billingClient)

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

        // when and then
        assertDoesNotThrow {
            googleBillingConsumer.consume(purchaseToken)
        }
    }

    @Test
    fun `consuming error`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        // when
        val exception = try {
            googleBillingConsumer.consume(purchaseToken)
            null
        } catch (e: Exception) {
            e
        }

        // then
        assertThat(exception).isInstanceOf(QonversionException::class.java)
        assertThat((exception as QonversionException).code).isEqualTo(ErrorCode.Consuming)
        assertThat(exception.message).contains(purchaseToken, "ERROR")
    }

    @Test
    fun `acknowledging success`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.OK

        // when and then
        assertDoesNotThrow {
            googleBillingConsumer.acknowledge(purchaseToken)
        }
    }

    @Test
    fun `acknowledging error`() {
        // given
        every {
            billingResult.responseCode
        } returns BillingClient.BillingResponseCode.ERROR

        // when
        val exception = try {
            googleBillingConsumer.acknowledge(purchaseToken)
            null
        } catch (e: Exception) {
            e
        }

        // then
        assertThat(exception).isInstanceOf(QonversionException::class.java)
        assertThat((exception as QonversionException).code).isEqualTo(ErrorCode.Acknowledging)
        assertThat(exception.message).contains(purchaseToken, "ERROR")
    }
}