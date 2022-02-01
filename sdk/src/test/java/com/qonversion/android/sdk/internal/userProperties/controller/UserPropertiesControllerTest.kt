package com.qonversion.android.sdk.internal.userProperties.controller

import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coInvoke
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
    private lateinit var spykController: UserPropertiesControllerImpl

    @BeforeEach
    fun setUp() {
        every { mockLogger.warn(capture(slotLoggerWarningMessage)) } just runs
        every { mockLogger.error(capture(slotLoggerErrorMessage)) } just runs
        every { mockLogger.error(capture(slotLoggerErrorMessage), any()) } just runs

        controller = UserPropertiesControllerImpl(
            mockStorage,
            mockService,
            mockDelayedWorker,
            logger = mockLogger
        )
        spykController = spyk(controller)
    }

    @Nested
    inner class SetPropertyMethodsTest {

        @BeforeEach
        fun setUp() {
            coEvery { spykController.sendUserPropertiesIfNeeded() } just runs
        }

        @Test
        fun `single valid property`() {
            // given
            val key = "test_key"
            val value = "test value"
            every { spykController.isValidUserProperty(key, value) } returns true

            val slotKey = slot<String>()
            val slotValue = slot<String>()
            every { mockStorage.add(capture(slotKey), capture(slotValue)) } just runs

            // when
            spykController.setProperty(key, value)

            // then
            verifyOrder {
                spykController.isValidUserProperty(key, value)
                mockStorage.add(key, value)
                spykController.sendUserPropertiesIfNeeded()
            }
            assertThat(slotKey.captured).isEqualTo(key)
            assertThat(slotValue.captured).isEqualTo(value)
        }

        @Test
        fun `single invalid property`() {
            // given
            val key = "test key"
            val value = "test value"
            every { spykController.isValidUserProperty(key, value) } returns false

            // when
            spykController.setProperty(key, value)

            // then
            verifyOrder {
                spykController.isValidUserProperty(key, value)
                spykController.sendUserPropertiesIfNeeded()
            }
            verify { mockStorage wasNot called }
        }

        @Test
        fun `multiple valid properties`() {
            // given
            val properties = mapOf("one" to "three", "to be or not" to "be")
            every { spykController.isValidUserProperty(any(), any()) } returns true

            val slotProperties = slot<Map<String, String>>()
            every { mockStorage.add(capture(slotProperties)) } just runs

            // when
            spykController.setProperties(properties)

            // then
            verifyOrder {
                spykController.isValidUserProperty("one", "three")
                spykController.isValidUserProperty("to be or not", "be")
                mockStorage.add(properties)
                spykController.sendUserPropertiesIfNeeded()
            }
            assertThat(slotProperties.captured).isEqualTo(properties)
        }

        @Test
        fun `multiple invalid properties`() {
            // given
            val properties = mapOf("one" to "three", "to be or not" to "be")
            every { spykController.isValidUserProperty(any(), any()) } returns false

            val slotProperties = slot<Map<String, String>>()
            every { mockStorage.add(capture(slotProperties)) } just runs

            // when
            spykController.setProperties(properties)

            // then
            verifyOrder {
                spykController.isValidUserProperty("one", "three")
                spykController.isValidUserProperty("to be or not", "be")
                mockStorage.add(emptyMap())
                spykController.sendUserPropertiesIfNeeded()
            }
            assertThat(slotProperties.captured).isEmpty()
        }

        @Test
        fun `multiple properties with several valid`() {
            // given
            val validProperties = mapOf("one" to "three")
            val invalidProperties = mapOf("to be or not" to "be")
            val properties = validProperties + invalidProperties
            every { spykController.isValidUserProperty("one", "three") } returns true
            every { spykController.isValidUserProperty("to be or not", "be") } returns false

            val slotProperties = slot<Map<String, String>>()
            every { mockStorage.add(capture(slotProperties)) } just runs

            // when
            spykController.setProperties(properties)

            // then
            verifyOrder {
                spykController.isValidUserProperty("one", "three")
                spykController.isValidUserProperty("to be or not", "be")
                mockStorage.add(validProperties)
                spykController.sendUserPropertiesIfNeeded()
            }
            assertThat(slotProperties.captured).isEqualTo(validProperties)
        }
    }

    @Nested
    inner class SendUserPropertiesIfNeededTest {

        @BeforeEach
        fun setUp() {
            coEvery { spykController.sendUserProperties() } just runs
        }

        @Test
        fun `non-empty properties`() {
            // given
            val properties = mapOf("one" to "three")
            every { mockStorage.properties } returns properties
            every {
                mockDelayedWorker.doDelayed(any(), any(), captureLambda())
            } answers {
                lambda<suspend () -> Unit>().coInvoke()
            }

            // when
            spykController.sendUserPropertiesIfNeeded()

            // then
            coVerifyOrder {
                mockStorage.properties
                mockDelayedWorker.doDelayed(5000, false, any())
                spykController.sendUserProperties()
            }
        }

        @Test
        fun `empty properties`() {
            // given
            val properties = emptyMap<String, String>()
            every { mockStorage.properties } returns properties

            // when
            spykController.sendUserPropertiesIfNeeded()

            // then
            verify(exactly = 1) { mockStorage.properties }
            coVerify(exactly = 0) {
                mockDelayedWorker.doDelayed(any(), any(), any())
                spykController.sendUserProperties()
            }
        }

        @Test
        fun `ignoring existing job`() {
            // given
            val properties = mapOf("one" to "three")
            every { mockStorage.properties } returns properties
            every {
                mockDelayedWorker.doDelayed(any(), any(), captureLambda())
            } answers {
                lambda<suspend () -> Unit>().coInvoke()
            }

            // when
            spykController.sendUserPropertiesIfNeeded(ignoreExistingJob = true)

            // then
            coVerifyOrder {
                mockStorage.properties
                mockDelayedWorker.doDelayed(5000, true, any())
                spykController.sendUserProperties()
            }
        }

        @Test
        fun `custom delay`() {
            // given
            val customDelay = 1000L
            val spykController = spyk(
                UserPropertiesControllerImpl(
                    mockStorage,
                    mockService,
                    mockDelayedWorker,
                    sendingDelayMs = customDelay,
                    logger = mockLogger
                )
            )
            coEvery { spykController.sendUserProperties() } just runs

            val properties = mapOf("one" to "three")
            every { mockStorage.properties } returns properties
            every {
                mockDelayedWorker.doDelayed(any(), any(), captureLambda())
            } answers {
                lambda<suspend () -> Unit>().coInvoke()
            }

            // when
            spykController.sendUserPropertiesIfNeeded()

            // then
            coVerifyOrder {
                mockStorage.properties
                mockDelayedWorker.doDelayed(customDelay, false, any())
                spykController.sendUserProperties()
            }
        }
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class SendUserPropertiesTest {

        @BeforeEach
        fun setUp() {
            every {
                spykController.sendUserPropertiesIfNeeded(any())
            } just runs
        }

        @Test
        fun `successfully send properties`() = runTest {
            // given
            val properties = mapOf("one" to "three")
            every { mockStorage.properties }.returns(properties)
            every { mockStorage.delete(properties) } just runs

            val processedProperties = properties.keys.toList()
            coEvery {
                mockService.sendProperties(properties)
            } returns processedProperties

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockStorage.properties
                mockService.sendProperties(properties)
                mockStorage.delete(properties)
                spykController.sendUserPropertiesIfNeeded(true)
            }
            verify { mockLogger wasNot called }
        }

        @Test
        fun `send empty properties`() = runTest {
            // given
            val properties = emptyMap<String, String>()
            every { mockStorage.properties }.returns(properties)

            // when
            spykController.sendUserProperties()

            // then
            verify { mockService wasNot called }
            verify(exactly = 0) {
                mockStorage.delete(any())
                spykController.sendUserPropertiesIfNeeded(any())
            }
            verify { mockLogger wasNot called }
        }

        @Test
        fun `failed to send properties`() = runTest {
            // given
            val properties = mapOf("one" to "three")
            every { mockStorage.properties }.returns(properties)

            val exception = QonversionException(ErrorCode.BackendError)
            coEvery {
                mockService.sendProperties(properties)
            } throws exception

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockStorage.properties
                mockService.sendProperties(properties)
                mockLogger.error(any(), exception)
            }
            verify(exactly = 0) {
                mockStorage.delete(any())
                spykController.sendUserPropertiesIfNeeded(any())
            }
            assertThat(slotLoggerErrorMessage.captured)
                .isEqualTo("Failed to send user properties to api")
        }

        @Test
        fun `not all properties were processed`() = runTest {
            // given
            val properties = mapOf(
                "one" to "three",
                "to be or not" to "be",
                "s" to "rm"
            )
            every { mockStorage.properties }.returns(properties)
            every { mockStorage.delete(properties) } just runs

            val processedProperties = listOf("to be or not")
            coEvery {
                mockService.sendProperties(properties)
            } returns processedProperties

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockStorage.properties
                mockService.sendProperties(properties)
                mockStorage.delete(properties)
                mockLogger.warn(any())
                spykController.sendUserPropertiesIfNeeded(true)
            }
            assertThat(slotLoggerWarningMessage.captured)
                .isEqualTo("Some user properties were not processed: one, s.")
        }

        @Test
        fun `stored properties changed while network request`() = runTest {
            // given
            val properties = mutableMapOf("one" to "three")
            val expectedProperties = properties.toMap()
            every { mockStorage.properties }.returns(properties)
            every { mockStorage.delete(expectedProperties) } just runs

            val processedProperties = properties.keys.toList()
            coEvery {
                mockService.sendProperties(properties)
            } answers {
                properties["newKey"] = "newValue"
                processedProperties
            }

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockStorage.properties
                mockService.sendProperties(expectedProperties)
                mockStorage.delete(expectedProperties)
                spykController.sendUserPropertiesIfNeeded(true)
            }
            verify { mockLogger wasNot called }
        }
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