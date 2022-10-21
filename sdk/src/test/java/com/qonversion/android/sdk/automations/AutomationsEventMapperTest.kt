package com.qonversion.android.sdk.automations

import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.internal.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class AutomationsEventMapperTest {

    private lateinit var automationsEventMapper: AutomationsEventMapper
    private val mockLogger: Logger = mockk(relaxed = true)

    private val timeInSec: Long = 1620000000

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        automationsEventMapper = AutomationsEventMapper(mockLogger)
    }

    @Nested
    inner class GetEventFromRemoteMessage {
        @Test
        fun `should return null when payload doesn't contain event`() {
            // given
            val json = null
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage.data)

            // then
            Assert.assertEquals(null, result)
        }

        @Test
        fun `should return null when payload contains empty event name`() {
            // given
            val json = "{\"name\": \"\", \"happened\": $timeInSec}"
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage.data)

            // then
            Assert.assertEquals(null, result)
        }

        @Test
        fun `should return null when payload contains unknown event name`() {
            // given
            val json = "{\"name\": \"some_event_name\", \"happened\": $timeInSec}"
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage.data)

            // then
            assertThat(result).isNull()
        }

        @Test
        fun `should return event with current date when payload doesn't contain event date`() {
            // given
            val json = "{\"name\": \"trial_started\"}"
            val mockMessage = mockRemoteMessage(json)
            mockCalendar(timeInSec)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage.data)

            // then
            assertThat(result).isNotNull
            Assert.assertEquals(timeInSec.secondsToMilliSeconds(), result?.date?.time)
        }

        @Test
        fun `should return correct event date when payload contains event date`() {
            // given
            val json = "{\"name\": \"trial_started\", \"happened\": $timeInSec}"
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage.data)

            // then
            assertThat(result).isNotNull
            Assert.assertEquals(timeInSec.secondsToMilliSeconds(), result?.date?.time)
        }

        @Test
        fun `should map event types correctly`() {
            // given
            val eventsMap = mapOf(
                "{\"name\": \"trial_started\", \"happened\": $timeInSec}" to AutomationsEventType.TrialStarted,
                "{\"name\": \"trial_converted\", \"happened\": $timeInSec}" to AutomationsEventType.TrialConverted,
                "{\"name\": \"trial_canceled\", \"happened\": $timeInSec}" to AutomationsEventType.TrialCanceled,
                "{\"name\": \"trial_billing_retry_entered\", \"happened\": $timeInSec}" to AutomationsEventType.TrialBillingRetry,
                "{\"name\": \"subscription_started\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionStarted,
                "{\"name\": \"subscription_renewed\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionRenewed,
                "{\"name\": \"subscription_refunded\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionRefunded,
                "{\"name\": \"subscription_canceled\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionCanceled,
                "{\"name\": \"subscription_billing_retry_entered\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionBillingRetry,
                "{\"name\": \"in_app_purchase\", \"happened\": $timeInSec}" to AutomationsEventType.InAppPurchase,
                "{\"name\": \"subscription_upgraded\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionUpgraded,
                "{\"name\": \"trial_still_active\", \"happened\": $timeInSec}" to AutomationsEventType.TrialStillActive,
                "{\"name\": \"trial_expired\", \"happened\": $timeInSec}" to AutomationsEventType.TrialExpired,
                "{\"name\": \"subscription_expired\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionExpired,
                "{\"name\": \"subscription_downgraded\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionDowngraded,
                "{\"name\": \"subscription_product_changed\", \"happened\": $timeInSec}" to AutomationsEventType.SubscriptionProductChanged
            )
            eventsMap.forEach { json ->
                val mockMessage = mockRemoteMessage(json.key)

                // when
                val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage.data)

                // then
                Assert.assertEquals(json.value, result?.type)
            }
        }

        private fun mockRemoteMessage(value: String?): RemoteMessage {
            val pickScreen = "qonv.event"

            val remoteMessage = mockk<RemoteMessage>()
            every {
                remoteMessage.data
            } returns mapOf(
                pickScreen to value
            )
            return remoteMessage
        }

        private fun mockCalendar(timeInSec: Long) {
            val calendar = mockk<Calendar>()
            mockkStatic(Calendar::class)
            every { Calendar.getInstance() } returns calendar
            every {
                calendar.timeInMillis
            } returns timeInSec.secondsToMilliSeconds()
        }
    }
}