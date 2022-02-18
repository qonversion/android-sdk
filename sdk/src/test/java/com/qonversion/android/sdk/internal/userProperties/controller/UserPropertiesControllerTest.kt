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

    private val mockSentPropertiesStorage = mockk<UserPropertiesStorage>()
    private val mockPendingPropertiesStorage = mockk<UserPropertiesStorage>()
    private val mockService = mockk<UserPropertiesService>()
    private val mockDelayedWorker = mockk<DelayedWorker>()
    private val mockLogger = mockk<Logger>()
    private val slotLoggerWarningMessage = slot<String>()
    private val slotLoggerErrorMessage = slot<String>()
    private val slotLoggerInfoMessage = slot<String>()
    private lateinit var controller: UserPropertiesControllerImpl
    private lateinit var spykController: UserPropertiesControllerImpl

    @BeforeEach
    fun setUp() {
        every { mockLogger.info(capture(slotLoggerInfoMessage)) } just runs
        every { mockLogger.warn(capture(slotLoggerWarningMessage)) } just runs
        every { mockLogger.error(capture(slotLoggerErrorMessage)) } just runs
        every { mockLogger.error(capture(slotLoggerErrorMessage), any()) } just runs

        controller = UserPropertiesControllerImpl(
            mockPendingPropertiesStorage,
            mockSentPropertiesStorage,
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
            every { spykController.shouldSendProperty(key, value) } returns true

            val slotKey = slot<String>()
            val slotValue = slot<String>()
            every { mockPendingPropertiesStorage.add(capture(slotKey), capture(slotValue)) } just runs

            // when
            spykController.setProperty(key, value)

            // then
            verifyOrder {
                spykController.shouldSendProperty(key, value)
                mockPendingPropertiesStorage.add(key, value)
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
            every { spykController.shouldSendProperty(key, value) } returns false

            // when
            spykController.setProperty(key, value)

            // then
            verifyOrder {
                spykController.shouldSendProperty(key, value)
                spykController.sendUserPropertiesIfNeeded()
            }
            verify { mockPendingPropertiesStorage wasNot called }
        }

        @Test
        fun `multiple valid properties`() {
            // given
            val properties = mapOf("one" to "three", "to be or not" to "be")
            every { spykController.shouldSendProperty(any(), any()) } returns true

            val slotProperties = slot<Map<String, String>>()
            every { mockPendingPropertiesStorage.add(capture(slotProperties)) } just runs

            // when
            spykController.setProperties(properties)

            // then
            verifyOrder {
                spykController.shouldSendProperty("one", "three")
                spykController.shouldSendProperty("to be or not", "be")
                mockPendingPropertiesStorage.add(properties)
                spykController.sendUserPropertiesIfNeeded()
            }
            assertThat(slotProperties.captured).isEqualTo(properties)
        }

        @Test
        fun `multiple invalid properties`() {
            // given
            val properties = mapOf("one" to "three", "to be or not" to "be")
            every { spykController.shouldSendProperty(any(), any()) } returns false

            val slotProperties = slot<Map<String, String>>()
            every { mockPendingPropertiesStorage.add(capture(slotProperties)) } just runs

            // when
            spykController.setProperties(properties)

            // then
            verifyOrder {
                spykController.shouldSendProperty("one", "three")
                spykController.shouldSendProperty("to be or not", "be")
                mockPendingPropertiesStorage.add(emptyMap())
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
            every { spykController.shouldSendProperty("one", "three") } returns true
            every { spykController.shouldSendProperty("to be or not", "be") } returns false

            val slotProperties = slot<Map<String, String>>()
            every { mockPendingPropertiesStorage.add(capture(slotProperties)) } just runs

            // when
            spykController.setProperties(properties)

            // then
            verifyOrder {
                spykController.shouldSendProperty("one", "three")
                spykController.shouldSendProperty("to be or not", "be")
                mockPendingPropertiesStorage.add(validProperties)
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
            every { mockPendingPropertiesStorage.properties } returns properties
            every {
                mockDelayedWorker.doDelayed(any(), any(), captureLambda())
            } answers {
                lambda<suspend () -> Unit>().coInvoke()
            }

            // when
            spykController.sendUserPropertiesIfNeeded()

            // then
            coVerifyOrder {
                mockPendingPropertiesStorage.properties
                mockDelayedWorker.doDelayed(5000, false, any())
                spykController.sendUserProperties()
            }
        }

        @Test
        fun `empty properties`() {
            // given
            val properties = emptyMap<String, String>()
            every { mockPendingPropertiesStorage.properties } returns properties

            // when
            spykController.sendUserPropertiesIfNeeded()

            // then
            verify(exactly = 1) { mockPendingPropertiesStorage.properties }
            coVerify(exactly = 0) {
                mockDelayedWorker.doDelayed(any(), any(), any())
                spykController.sendUserProperties()
            }
        }

        @Test
        fun `ignoring existing job`() {
            // given
            val properties = mapOf("one" to "three")
            every { mockPendingPropertiesStorage.properties } returns properties
            every {
                mockDelayedWorker.doDelayed(any(), any(), captureLambda())
            } answers {
                lambda<suspend () -> Unit>().coInvoke()
            }

            // when
            spykController.sendUserPropertiesIfNeeded(ignoreExistingJob = true)

            // then
            coVerifyOrder {
                mockPendingPropertiesStorage.properties
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
                    mockPendingPropertiesStorage,
                    mockSentPropertiesStorage,
                    mockService,
                    mockDelayedWorker,
                    sendingDelayMs = customDelay,
                    logger = mockLogger
                )
            )
            coEvery { spykController.sendUserProperties() } just runs

            val properties = mapOf("one" to "three")
            every { mockPendingPropertiesStorage.properties } returns properties
            every {
                mockDelayedWorker.doDelayed(any(), any(), captureLambda())
            } answers {
                lambda<suspend () -> Unit>().coInvoke()
            }

            // when
            spykController.sendUserPropertiesIfNeeded()

            // then
            coVerifyOrder {
                mockPendingPropertiesStorage.properties
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
            every { mockPendingPropertiesStorage.properties }.returns(properties)
            every { mockPendingPropertiesStorage.delete(properties) } just runs
            every { mockSentPropertiesStorage.add(properties) } just runs

            val processedProperties = properties.keys.toList()
            coEvery {
                mockService.sendProperties(properties)
            } returns processedProperties

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockPendingPropertiesStorage.properties
                mockService.sendProperties(properties)
                mockPendingPropertiesStorage.delete(properties)
                mockSentPropertiesStorage.add(properties)
                spykController.sendUserPropertiesIfNeeded(true)
            }
            verify { mockLogger wasNot called }
        }

        @Test
        fun `send empty properties`() = runTest {
            // given
            val properties = emptyMap<String, String>()
            every { mockPendingPropertiesStorage.properties }.returns(properties)

            // when
            spykController.sendUserProperties()

            // then
            verify {
                mockService wasNot called
                mockLogger wasNot called
            }
            verify(exactly = 0) {
                mockPendingPropertiesStorage.delete(any())
                spykController.sendUserPropertiesIfNeeded(any())
            }
        }

        @Test
        fun `failed to send properties`() = runTest {
            // given
            val properties = mapOf("one" to "three")
            every { mockPendingPropertiesStorage.properties }.returns(properties)

            val exception = QonversionException(ErrorCode.BackendError)
            coEvery {
                mockService.sendProperties(properties)
            } throws exception

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockPendingPropertiesStorage.properties
                mockService.sendProperties(properties)
                mockLogger.error(any(), exception)
            }
            verify(exactly = 0) {
                mockPendingPropertiesStorage.delete(any())
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
            val processedPropertiesKeys = listOf("to be or not")
            coEvery {
                mockService.sendProperties(properties)
            } returns processedPropertiesKeys

            val processedProperties = properties.filter { processedPropertiesKeys.contains(it.key) }

            every { mockPendingPropertiesStorage.properties }.returns(properties)
            every { mockPendingPropertiesStorage.delete(properties) } just runs
            every { mockSentPropertiesStorage.add(processedProperties) } just runs

            // when
            spykController.sendUserProperties()

            // then
            coVerifyOrder {
                mockPendingPropertiesStorage.properties
                mockService.sendProperties(properties)
                mockPendingPropertiesStorage.delete(properties)
                mockSentPropertiesStorage.add(processedProperties)
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
            every { mockPendingPropertiesStorage.properties }.returns(properties)
            every { mockPendingPropertiesStorage.delete(expectedProperties) } just runs
            every { mockSentPropertiesStorage.add(expectedProperties) } just runs

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
                mockPendingPropertiesStorage.properties
                mockService.sendProperties(expectedProperties)
                mockPendingPropertiesStorage.delete(expectedProperties)
                mockSentPropertiesStorage.add(expectedProperties)
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
            every { mockSentPropertiesStorage.properties}.returns(emptyMap())

            // when
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isTrue

            verify { mockLogger wasNot called }
        }

        @Test
        fun `already sent user property`() {
            // given
            val key = "test_key"
            val value = "test value"
            val sentProperties = mapOf(key to value)

            every { mockSentPropertiesStorage.properties}.returns(sentProperties)

            val expInfoMessage =
                """The same property with key: "$key" and value: "$value" 
                |was already sent for the current user. 
                |To avoid any confusion, it will not be sent again.""".trimMargin()

            // when
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotLoggerInfoMessage.captured).isEqualTo(expInfoMessage)
            verify(exactly = 1) {
                mockLogger.info(expInfoMessage)
            }
        }

        @Test
        fun `already sent invalid user property`() {
            // given
            val key = "  "
            val value = "test value"
            val sentProperties = mapOf(key to value)

            every { mockSentPropertiesStorage.properties}.returns(sentProperties)

            val expErrorMessage =
                """Invalid key "$key" for user property. 
                    |The key should be nonempty and may consist of letters A-Za-z, 
                    |numbers, and symbols _.:-.""".trimMargin()

            val expInfoMessage =
                """The same property with key: "$key" and value: "$value" 
                |was already sent for the current user. 
                |To avoid any confusion, it will not be sent again.""".trimMargin()

            // when
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotLoggerErrorMessage.captured).isEqualTo(expErrorMessage)

            verify(exactly = 0) {
                mockLogger.info(expInfoMessage)
                mockSentPropertiesStorage.properties
            }
        }

        @Test
        fun `already sent property with the same key and another value`() {
            // given
            val key = "test_key"
            val value = "test value"
            val sentProperties = mapOf(key to "other value")

            every { mockSentPropertiesStorage.properties}.returns(sentProperties)

            // when
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isTrue

            verify { mockLogger wasNot called }
        }

        @Test
        fun `already sent property with the same value and another key`() {
            // given
            val key = "test_key"
            val value = "test value"
            val sentProperties = mapOf("some other key" to value)

            every { mockSentPropertiesStorage.properties}.returns(sentProperties)

            // when
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isTrue

            verify { mockLogger wasNot called }
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
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotLoggerErrorMessage.captured).isEqualTo(expErrorMessage)

            verify(exactly = 1) {
                mockLogger.error(expErrorMessage)
            }
        }

        @Test
        fun `user property with invalid value`() {
            // given
            val key = "test_key"
            val value = ""
            val expErrorMessage = """The empty value provided for user property "$key"."""

            // when
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotLoggerErrorMessage.captured).isEqualTo(expErrorMessage)
            verify(exactly = 1) {
                mockLogger.error(expErrorMessage)
            }
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
            val result = controller.shouldSendProperty(key, value)

            // then
            assertThat(result).isFalse
            assertThat(slotErrorMessages).isEqualTo(expErrorMessages)
            verify(exactly = 1) {
                mockLogger.error(expErrorMessages[0])
                mockLogger.error(expErrorMessages[1])
            }
        }
    }
}