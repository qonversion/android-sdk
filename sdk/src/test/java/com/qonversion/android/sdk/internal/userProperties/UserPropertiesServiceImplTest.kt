package com.qonversion.android.sdk.internal.userProperties

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
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

    @BeforeEach
    fun setUp() {
        service = UserPropertiesServiceImpl(
            mockRequestConfigurator,
            mockApiInteractor,
            mockMapper
        )
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class SendPropertiesTest {
        @Test
        fun `send properties success`() = runTest {
            // given
            val expectedProperties = listOf("_q_email", "_q_adjust_adid")
            val responseData = mapOf("one" to "three")

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
                mockMapper.fromMap(responseData)
            } returns expectedProperties

            // when
            val result = service.sendProperties(propertiesMap)

            // then
            assertThat(result).isEqualTo(expectedProperties)
            coVerifyOrder {
                mockRequestConfigurator.configureUserPropertiesRequest(propertiesMap)
                mockApiInteractor.execute(mockRequest)
                mockMapper.fromMap(responseData)
            }
        }

        @Test
        fun `send properties error`() = runTest {
            // given
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
            coAssertThatQonversionExceptionThrown(ErrorCode.BackendError) {
                service.sendProperties(propertiesMap)
            }

            // then
            coVerifyOrder {
                mockRequestConfigurator.configureUserPropertiesRequest(propertiesMap)
                mockApiInteractor.execute(mockRequest)
            }
            verify { mockMapper wasNot called }
        }
    }
}