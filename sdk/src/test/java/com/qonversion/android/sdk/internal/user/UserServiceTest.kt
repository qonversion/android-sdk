package com.qonversion.android.sdk.internal.user

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
    private val mockLocalStorage = mockk<LocalStorage>()
    private val mockUser = mockk<User>()
    private val mockRequest = mockk<Request>()
    private lateinit var userService: UserServiceImpl

    @BeforeEach
    fun setUp() {
        userService = UserServiceImpl(
            mockRequestConfigurator,
            mockApiInteractor,
            mockMapper,
            mockLocalStorage
        )
    }

    @Nested
    inner class ObtainUserIdTest {

        private lateinit var spyUserService: UserServiceImpl

        @BeforeEach
        fun setUp() {
            spyUserService = spyk(userService)
        }

        @Test
        fun `cached user exists`() {
            // given
            val cachedUserId = "cached user id"
            every { mockLocalStorage.getString(StorageConstants.UserId.key) } returns cachedUserId

            // when
            val result = spyUserService.obtainUserId()

            // then
            assertThat(result).isEqualTo(cachedUserId)
            verify { mockLocalStorage.getString(StorageConstants.UserId.key) }
            verify(exactly = 0) {
                mockLocalStorage.getString(StorageConstants.Token.key)
                mockLocalStorage.remove(any())
                mockLocalStorage.putString(any(), any())
                spyUserService.generateRandomUserID()
            }
        }

        @Test
        fun `cached user is test`() {
            // given
            val cachedUserId = "40egafre6_e_"
            val expectedId = "some generated id"
            every { mockLocalStorage.getString(StorageConstants.UserId.key) } returns cachedUserId
            every { mockLocalStorage.putString(StorageConstants.UserId.key, expectedId) } just runs
            every { mockLocalStorage.putString(StorageConstants.OriginalUserId.key, expectedId) } just runs
            every { spyUserService.generateRandomUserID() } returns expectedId

            // when
            val result = spyUserService.obtainUserId()

            // then
            assertThat(result).isEqualTo(expectedId)
            verifyOrder {
                mockLocalStorage.getString(StorageConstants.UserId.key)
                spyUserService.generateRandomUserID()
                mockLocalStorage.putString(StorageConstants.UserId.key, expectedId)
                mockLocalStorage.putString(StorageConstants.OriginalUserId.key, expectedId)
            }
            verify(exactly = 0) {
                mockLocalStorage.getString(StorageConstants.Token.key)
                mockLocalStorage.remove(any())
            }
        }

        @Test
        fun `deprecated token exists`() {
            // given
            val deprecatedUserId = "deprecated user id"
            every { mockLocalStorage.getString(StorageConstants.UserId.key) } returns null
            every { mockLocalStorage.getString(StorageConstants.Token.key) } returns deprecatedUserId
            every { mockLocalStorage.remove(StorageConstants.Token.key) } just runs
            every { mockLocalStorage.putString(StorageConstants.UserId.key, deprecatedUserId) } just runs
            every { mockLocalStorage.putString(StorageConstants.OriginalUserId.key, deprecatedUserId) } just runs

            // when
            val result = spyUserService.obtainUserId()

            // then
            assertThat(result).isEqualTo(deprecatedUserId)
            verifyOrder {
                mockLocalStorage.getString(StorageConstants.UserId.key)
                mockLocalStorage.getString(StorageConstants.Token.key)
                mockLocalStorage.remove(StorageConstants.Token.key)
                mockLocalStorage.putString(StorageConstants.UserId.key, deprecatedUserId)
                mockLocalStorage.putString(StorageConstants.OriginalUserId.key, deprecatedUserId)
            }
            verify(exactly = 0) {
                spyUserService.generateRandomUserID()
            }
        }

        @Test
        fun `deprecated token exists and it's test`() {
            // given
            val deprecatedTestUserId = "40egafre6_e_"
            val expectedId = "some generated id"
            every { mockLocalStorage.getString(StorageConstants.UserId.key) } returns null
            every { mockLocalStorage.getString(StorageConstants.Token.key) } returns deprecatedTestUserId
            every { mockLocalStorage.remove(StorageConstants.Token.key) } just runs
            every { mockLocalStorage.putString(StorageConstants.UserId.key, expectedId) } just runs
            every { mockLocalStorage.putString(StorageConstants.OriginalUserId.key, expectedId) } just runs
            every { spyUserService.generateRandomUserID() } returns expectedId

            // when
            val result = spyUserService.obtainUserId()

            // then
            assertThat(result).isEqualTo(expectedId)
            verifyOrder {
                mockLocalStorage.getString(StorageConstants.UserId.key)
                mockLocalStorage.getString(StorageConstants.Token.key)
                mockLocalStorage.remove(StorageConstants.Token.key)
                spyUserService.generateRandomUserID()
                mockLocalStorage.putString(StorageConstants.UserId.key, expectedId)
                mockLocalStorage.putString(StorageConstants.OriginalUserId.key, expectedId)
            }
        }

        @Test
        fun `new user`() {
            // given
            val expectedId = "some generated id"
            every { mockLocalStorage.getString(StorageConstants.UserId.key) } returns null
            every { mockLocalStorage.getString(StorageConstants.Token.key) } returns null
            every { mockLocalStorage.remove(StorageConstants.Token.key) } just runs
            every { mockLocalStorage.putString(StorageConstants.UserId.key, expectedId) } just runs
            every { mockLocalStorage.putString(StorageConstants.OriginalUserId.key, expectedId) } just runs
            every { spyUserService.generateRandomUserID() } returns expectedId

            // when
            val result = spyUserService.obtainUserId()

            // then
            assertThat(result).isEqualTo(expectedId)
            verifyOrder {
                mockLocalStorage.getString(StorageConstants.UserId.key)
                mockLocalStorage.getString(StorageConstants.Token.key)
                mockLocalStorage.remove(StorageConstants.Token.key)
                spyUserService.generateRandomUserID()
                mockLocalStorage.putString(StorageConstants.UserId.key, expectedId)
                mockLocalStorage.putString(StorageConstants.OriginalUserId.key, expectedId)
            }
        }
    }

    @Nested
    inner class UpdateCurrentUserIdTest {

        @Test
        fun `update id`() {
            // given
            val testUserId = "test id"
            every { mockLocalStorage.putString(StorageConstants.UserId.key, testUserId) } just runs

            // when
            userService.updateCurrentUserId(testUserId)

            // then
            verify {
                mockLocalStorage.putString(StorageConstants.UserId.key, testUserId)
            }
        }
    }

    @Nested
    inner class LogoutIfNeededTest {

        @Test
        fun `logout is not needed`() {
            // given
            val userId = "test user id"
            every { mockLocalStorage.getString(StorageConstants.OriginalUserId.key, "") } returns userId
            every { mockLocalStorage.getString(StorageConstants.UserId.key, "") } returns userId

            // when
            val result = userService.logoutIfNeeded()

            // then
            assertThat(result).isFalse
            verify {
                mockLocalStorage.getString(StorageConstants.OriginalUserId.key, "")
                mockLocalStorage.getString(StorageConstants.UserId.key, "")
            }
            verify(exactly = 0) {
                mockLocalStorage.putString(any(), any())
            }
        }

        @Test
        fun `logout is needed`() {
            // given
            val originalUserId = "original test user id"
            val userId = "test user id"
            every { mockLocalStorage.getString(StorageConstants.OriginalUserId.key, "") } returns originalUserId
            every { mockLocalStorage.getString(StorageConstants.UserId.key, "") } returns userId
            every { mockLocalStorage.putString(StorageConstants.UserId.key, originalUserId) } just runs

            // when
            val result = userService.logoutIfNeeded()

            // then
            assertThat(result).isTrue
            verifyOrder {
                mockLocalStorage.getString(StorageConstants.OriginalUserId.key, "")
                mockLocalStorage.getString(StorageConstants.UserId.key, "")
                mockLocalStorage.putString(StorageConstants.UserId.key, originalUserId)
            }
        }
    }

    @Nested
    inner class ResetUserTest {

        @Test
        fun `successful reset`() {
            // given
            val keys = mutableListOf<String>()
            every { mockLocalStorage.remove(capture(keys)) } just runs
            val expectedKeys = listOf(
                StorageConstants.OriginalUserId.key,
                StorageConstants.UserId.key,
                StorageConstants.Token.key
            )

            // when
            userService.resetUser()

            // then
            verify(exactly = expectedKeys.size) {
                mockLocalStorage.remove(any())
            }
            assertThat(keys).containsExactlyElementsOf(expectedKeys)
        }
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

    @Nested
    inner class GenerateRandomUserIdTest {

        @Test
        fun `format check`() {
            val ids = (1..10).map {
                // given
                val regex = Regex("""^QON_[a-zA-Z\d]{32}$""")

                // when
                val id = userService.generateRandomUserID()

                // then
                assertThat(regex.matches(id)).isTrue
                id
            }
            // check for duplicates
            assertThat(ids.size).isEqualTo(ids.toSet().size)
        }
    }
}
