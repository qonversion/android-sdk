package com.qonversion.android.sdk.internal.user.service

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection

internal class UserServiceTest {

    private val mockRequestConfigurator = mockk<RequestConfigurator>()
    private val mockApiInteractor = mockk<ApiInteractor>()
    private val mockMapper = mockk<UserMapper>()
    private val mockUser = mockk<User>()
    private val mockRequest = mockk<Request>()
    private lateinit var userService: UserServiceImpl

    @BeforeEach
    fun setUp() {
        userService = UserServiceImpl(
            mockRequestConfigurator,
            mockApiInteractor,
            mockMapper
        )
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class GetUserTest {

        private val testUserId = "test id"

        @BeforeEach
        fun setUp() {
            every {
                mockRequestConfigurator.configureUserRequest(testUserId)
            } returns mockRequest
        }

        @Test
        fun `user successfully got`() = runTest {
            // given
            val response = Response.Success(200, Unit)
            coEvery { mockApiInteractor.execute(mockRequest) } returns response

            val spyUserService = spyk(userService)
            every { spyUserService.mapUser(response) } returns mockUser

            // when
            val result = spyUserService.getUser(testUserId)

            // then
            assertThat(result).isSameAs(mockUser)
            coVerifyOrder {
                mockRequestConfigurator.configureUserRequest(testUserId)
                mockApiInteractor.execute(mockRequest)
                spyUserService.mapUser(response)
            }
        }

        @Test
        fun `api request failed`() = runTest {
            // given
            val spyUserService = spyk(userService)
            val response = Response.Error(500, "Test error")
            coEvery { mockApiInteractor.execute(mockRequest) } returns response

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.BackendError) {
                spyUserService.getUser(testUserId)
            }

            // then
            coVerifyOrder {
                mockRequestConfigurator.configureUserRequest(testUserId)
                mockApiInteractor.execute(mockRequest)
            }
            verify(exactly = 0) { spyUserService.mapUser(any()) }
        }

        @Test
        fun `user does not exist`() = runTest {
            // given
            val response = Response.Error(HttpURLConnection.HTTP_NOT_FOUND, "Test error")
            coEvery { mockApiInteractor.execute(mockRequest) } returns response

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.UserNotFound) {
                userService.getUser(testUserId)
            }

            // then
            coVerifyOrder {
                mockRequestConfigurator.configureUserRequest(testUserId)
                mockApiInteractor.execute(mockRequest)
            }
        }
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class CreateUserTest {

        private val testUserId = "test id"

        @BeforeEach
        fun setUp() {
            every {
                mockRequestConfigurator.configureCreateUserRequest(testUserId)
            } returns mockRequest
        }

        @Test
        fun `user successfully created`() = runTest {
            // given
            val response = Response.Success(200, Unit)
            coEvery { mockApiInteractor.execute(mockRequest) } returns response

            val spyUserService = spyk(userService)
            every { spyUserService.mapUser(response) } returns mockUser

            // when
            val result = spyUserService.createUser(testUserId)

            // then
            assertThat(result).isSameAs(mockUser)
            coVerifyOrder {
                mockRequestConfigurator.configureCreateUserRequest(testUserId)
                mockApiInteractor.execute(mockRequest)
                spyUserService.mapUser(response)
            }
        }

        @Test
        fun `api request failed`() = runTest {
            // given
            val spyUserService = spyk(userService)
            val response = Response.Error(500, "Test error")
            coEvery { mockApiInteractor.execute(mockRequest) } returns response

            // when
            coAssertThatQonversionExceptionThrown(ErrorCode.BackendError) {
                spyUserService.createUser(testUserId)
            }

            // then
            coVerifyOrder {
                mockRequestConfigurator.configureCreateUserRequest(testUserId)
                mockApiInteractor.execute(mockRequest)
            }
            verify(exactly = 0) { spyUserService.mapUser(any()) }
        }
    }

    @Nested
    inner class MapUserTest {

        private val responseData = mapOf("one" to "three")
        private val response = Response.Success(200, responseData)

        @Test
        fun `successful mapping`() {
            // given
            every { mockMapper.fromMap(responseData) } returns mockUser

            // when
            val user = userService.mapUser(response)

            // then
            verify { mockMapper.fromMap(responseData) }
            assertThat(user).isSameAs(mockUser)
        }

        @Test
        fun `mapping failed`() {
            // given
            every { mockMapper.fromMap(responseData) } returns null

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Mapping) {
                userService.mapUser(response)
            }

            // then
            verify { mockMapper.fromMap(responseData) }
        }
    }
}
