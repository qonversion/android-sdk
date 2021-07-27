package com.qonversion.android.sdk.automations

import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.logger.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage)

            // then
            Assert.assertEquals(null, result)
        }

        @Test
        fun `should return null when payload contains empty event name`() {
            // given
            val json = "{\"name\": \"\", \"happened\": $timeInSec}"
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage)

            // then
            Assert.assertEquals(null, result)
        }

        @Test
        fun `should return event with current date when payload doesn't contain event date`() {
            // given
            val json = "{\"name\": \"trial_started\"}"
            val mockMessage = mockRemoteMessage(json)
            mockCalendar(timeInSec)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage)

            // then
            assertThat(result).isNotNull
            assertAll(
                { Assert.assertEquals(AutomationsEventType.TrialStarted, result?.type) },
                { Assert.assertEquals(timeInSec.secondsToMilliSeconds(), result?.date?.time) }
            )
        }

        @Test
        fun `should return correct event when payload contains event name and date`() {
            // given
            val json = "{\"name\": \"trial_started\", \"happened\": $timeInSec}"
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage)

            // then
            assertThat(result).isNotNull
            assertAll(
                { Assert.assertEquals(AutomationsEventType.TrialStarted, result?.type) },
                { Assert.assertEquals(timeInSec.secondsToMilliSeconds(), result?.date?.time) }
            )
        }

        @Test
        fun `should return event with unknown type when payload contains unknown event name`() {
            // given
            val json = "{\"name\": \"some_event_name\", \"happened\": $timeInSec}"
            val mockMessage = mockRemoteMessage(json)

            // when
            val result = automationsEventMapper.getEventFromRemoteMessage(mockMessage)

            // then
            assertThat(result).isNotNull
            assertAll(
                { Assert.assertEquals(AutomationsEventType.Unknown, result?.type) },
                { Assert.assertEquals(timeInSec.secondsToMilliSeconds(), result?.date?.time) }
            )
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