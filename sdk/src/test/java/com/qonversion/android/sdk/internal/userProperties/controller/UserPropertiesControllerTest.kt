package com.qonversion.android.sdk.internal.userProperties.controller

import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class UserPropertiesControllerTest {

    private val mockStorage = mockk<UserPropertiesStorage>()
    private val mockService = mockk<UserPropertiesService>()
    private val mockDelayedWorker = mockk<DelayedWorker>()
    private val mockLogger = mockk<Logger>()
    private val slotLoggerWarningMessage = slot<String>()
    private val slotLoggerErrorMessage = slot<String>()
    private lateinit var controller: UserPropertiesControllerImpl

    @BeforeEach
    fun setUp() {
        every { mockLogger.warn(capture(slotLoggerWarningMessage)) } just runs
        every { mockLogger.error(capture(slotLoggerErrorMessage)) } just runs

        controller = UserPropertiesControllerImpl(
            mockStorage,
            mockService,
            mockDelayedWorker,
            logger = mockLogger
        )
    }

    @Nested
    inner class ValidatorTest {

        @Test
        fun values() {
            mapOf(
                "test value" to true,
                "" to false
            ).forEach { (value, expResult) ->
                // when
                val result = controller.isValidValue(value)

                // then
                assertThat(result).isEqualTo(expResult)
            }
        }

        @Test
        fun keys() {
            mapOf(
                "test_key" to true, // correct key
                UserProperty.FacebookAttribution.code to true, // defined key
                "" to false, // empty key
                "  " to false, // blank key
                "test key" to false // incorrect key
            ).forEach { (key, expResult) ->
                // when
                val result = controller.isValidKey(key)

                // then
                assertThat(result).isEqualTo(expResult)
            }
        }

        @Test
        fun `valid user property`() {
            // given
            val key = "test_key"
            val value = "test value"

            // when
            val result = controller.isValidUserProperty(key, value)

            // then
            assertThat(result).isTrue
        }

        @Test
        fun `user property with invalid key`() {
            // given
            val key = "test key"
            val value = "test value"
            val expErrorMessage =
                """Invalid key "$key" for user property. 
                    |The key should be nonempty and may consist of letters A-Za-z, 
                    |numbers, and symbols _.:-.""".trimMargin()

            // when
            val result = controller.isValidUserProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotLoggerErrorMessage.captured).isEqualTo(expErrorMessage)
        }

        @Test
        fun `user property with invalid value`() {
            // given
            val key = "test_key"
            val value = ""
            val expErrorMessage = """The empty value provided for user property "$key"."""

            // when
            val result = controller.isValidUserProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotLoggerErrorMessage.captured).isEqualTo(expErrorMessage)
        }

        @Test
        fun `user property with invalid key and value`() {
            // given
            val key = "test key"
            val value = ""
            val expErrorMessages = listOf(
                """Invalid key "$key" for user property. 
                    |The key should be nonempty and may consist of letters A-Za-z, 
                    |numbers, and symbols _.:-.""".trimMargin(),
                """The empty value provided for user property "$key"."""
            )
            val slotErrorMessages = mutableListOf<String>()
            every { mockLogger.error(capture(slotErrorMessages)) } just runs

            // when
            val result = controller.isValidUserProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotErrorMessages).isEqualTo(expErrorMessages)
        }
    }
}