package com.qonversion.android.sdk.internal.userProperties

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested

import org.junit.jupiter.api.Test

internal class UserPropertiesServiceImplTest {
    private lateinit var service: UserPropertiesServiceImpl

    private val mockRequestConfigurator = mockk<RequestConfigurator>()
    private val mockApiInteractor = mockk<ApiInteractor>()
    private val mockMapper = mockk<UserPropertiesMapper>()
    private val mockLogger = mockk<Logger>()

    private val slotErrorLogMessage = slot<String>()

    @BeforeEach
    fun setUp() {
        every {
            mockLogger.error(capture(slotErrorLogMessage))
        } just runs

        service = UserPropertiesServiceImpl(
            mockRequestConfigurator,
            mockApiInteractor,
            mockMapper,
            mockLogger
        )
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class SendPropertiesTest {
        @Test
        fun `send properties success`() = runTest {
            // given
            val spykService = spyk(service)

            val expectedProperties = listOf("_q_email", "_q_adjust_adid")
            val responseData = mapOf(
                "result" to "ok",
                "error" to "",
                "errors" to listOf<String>(),
                "processed" to expectedProperties
            )

            val propertiesMap = mapOf(
                "key1" to "value1",
                "key2" to "value2"
            )
            val mockRequest = mockk<Request>()
            every {
                mockRequestConfigurator.configureUserPropertiesRequest(propertiesMap)
            } returns mockRequest

            val mockSuccessResponse = Response.Success(200, responseData)

            coEvery {
                mockApiInteractor.execute(mockRequest)
            } returns mockSuccessResponse

            every {
                spykService.mapProcessedProperties(mockSuccessResponse)
            } returns expectedProperties

            // when
            val result = spykService.sendProperties(propertiesMap)

            // then
            assertThat(result).isEqualTo(expectedProperties)
            coVerifyOrder {
                mockRequestConfigurator.configureUserPropertiesRequest(propertiesMap)
                mockApiInteractor.execute(mockRequest)
                spykService.mapProcessedProperties(mockSuccessResponse)
            }
        }

        @Test
        fun `send properties error`() = runTest {
            // given
            val spykService = spyk(service)

            val expectedProperties = emptyList<String>()

            val propertiesMap = mapOf(
                "key1" to "value1",
                "key2" to "value2"
            )
            val mockRequest = mockk<Request>()
            every {
                mockRequestConfigurator.configureUserPropertiesRequest(propertiesMap)
            } returns mockRequest

            val errorCode = 403
            val mockErrorResponse = Response.Error(errorCode, "message")

            coEvery {
                mockApiInteractor.execute(mockRequest)
            } returns mockErrorResponse

            // when
            val result = spykService.sendProperties(propertiesMap)

            // then
            assertThat(result).isEqualTo(expectedProperties)
            assertThat(slotErrorLogMessage.captured).isEqualTo("propertiesRequest ended with an error. Response code: $errorCode")

            coVerifyOrder {
                mockRequestConfigurator.configureUserPropertiesRequest(propertiesMap)
                mockApiInteractor.execute(mockRequest)
                mockLogger.error(any())
            }
            verify(exactly = 0) {
                spykService.mapProcessedProperties(any())
            }
        }
    }

    @Nested
    inner class MapHandledPropertiesTest {
        @Test
        fun `map processed properties success`() {
            // given
            val expectedProperties = listOf("_q_email", "_q_adjust_adid")
            val responseData = mapOf(
                "result" to "ok",
                "error" to "",
                "errors" to listOf<String>(),
                "processed" to expectedProperties
            )

            val mockSuccessResponse = Response.Success(200, responseData)
            every {
                mockMapper.fromMap(responseData)
            } returns expectedProperties

            // when
            val result = service.mapProcessedProperties(mockSuccessResponse)

            // then
            assertThat(result).isEqualTo(expectedProperties)
            verify(exactly = 1) {
                mockMapper.fromMap(responseData)
            }
        }

        @Test
        fun `map processed properties error`() {
            // given
            val responseData = arrayOf(
                "result",
                "error",
                "errors",
                "processed"
            )

            val mockSuccessResponse = Response.Success(200, responseData)

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Mapping) {
                service.mapProcessedProperties(mockSuccessResponse)
            }

            // then
            verify {
                mockMapper wasNot called
            }
        }
    }
}