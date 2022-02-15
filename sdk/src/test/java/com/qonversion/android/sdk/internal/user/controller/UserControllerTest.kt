package com.qonversion.android.sdk.internal.user.controller

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.CacheState
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.generator.UserIdGenerator
import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class UserControllerTest {

    private lateinit var userController: UserControllerImpl

    private val mockUserDataStorage = mockk<UserDataStorage>()
    private val mockUserIdGenerator = mockk<UserIdGenerator>()
    private val mockUserService = mockk<UserService>()
    private val mockUserCacher = mockk<Cacher<User?>>()
    private val mockLogger = mockk<Logger>()

    private val testUserId = "test user id"
    private val slotInfoLogMessage = slot<String>()
    private val slotErrorLogMessage = slot<String>()
    private val slotVerboseLogMessage = slot<String>()
    private val mockUser = mockk<User>()

    @BeforeEach
    fun setUp() {
        every { mockUserDataStorage.getUserId() } returns testUserId

        userController = UserControllerImpl(
            mockUserService,
            mockUserCacher,
            mockUserDataStorage,
            mockUserIdGenerator,
            mockLogger
        )

        every { mockLogger.info(capture(slotInfoLogMessage)) } just runs
        every { mockLogger.error(capture(slotErrorLogMessage)) } just runs
        every { mockLogger.error(capture(slotErrorLogMessage), any()) } just runs
        every { mockLogger.verbose(capture(slotVerboseLogMessage)) } just runs
    }

    @Nested
    inner class InitTest {

        @Test
        fun `user id exists`() {
            // given

            // when
            userController = UserControllerImpl(
                mockUserService,
                mockUserCacher,
                mockUserDataStorage,
                mockUserIdGenerator,
                mockLogger
            )

            // then
            verify { mockUserDataStorage.getUserId() }
            verify(exactly = 0) {
                mockUserIdGenerator.generate()
                mockUserDataStorage.setOriginalUserId(any())
            }
        }

        @Test
        fun `user id does not exist`() {
            // given
            every { mockUserDataStorage.getUserId() } returns null
            every { mockUserIdGenerator.generate() } returns testUserId
            every { mockUserDataStorage.setOriginalUserId(testUserId) } just runs

            // when
            userController = UserControllerImpl(
                mockUserService,
                mockUserCacher,
                mockUserDataStorage,
                mockUserIdGenerator,
                mockLogger
            )

            // then
            verifyOrder {
                mockUserDataStorage.getUserId()
                mockUserIdGenerator.generate()
                mockUserDataStorage.setOriginalUserId(testUserId)
            }
        }

        @Test
        fun `user id is test`() {
            // given
            every { mockUserDataStorage.getUserId() } returns "40egafre6_e_"
            every { mockUserIdGenerator.generate() } returns testUserId
            every { mockUserDataStorage.setOriginalUserId(testUserId) } just runs

            // when
            userController = UserControllerImpl(
                mockUserService,
                mockUserCacher,
                mockUserDataStorage,
                mockUserIdGenerator,
                mockLogger
            )

            // then
            verifyOrder {
                mockUserDataStorage.getUserId()
                mockUserIdGenerator.generate()
                mockUserDataStorage.setOriginalUserId(testUserId)
            }
        }
    }

    @Nested
    @ExperimentalCoroutinesApi
    inner class GetUserTest {
        private val userId = "userId"

        @Test
        fun `get user from cache with default state`() = runTest {
            // given
            every {
                mockUserCacher.getActual()
            } returns mockUser

            // when
            val result = userController.getUser()

            // then
            assertThat(result).isEqualTo(mockUser)
            assertThat(slotVerboseLogMessage.captured).isEqualTo("getUser() -> started")
        }

        @Test
        fun `get user from API successfully`() = runTest {
            // given
            userController = spyk(userController)

            every {
                mockUserCacher.getActual()
            } returns null

            coEvery {
                mockUserService.getUser(userId)
            } returns mockUser

            every {
                userController.storeUser(mockUser)
            } just runs

            // when
            val result = userController.getUser()

            // then
            assertThat(result).isEqualTo(mockUser)
            assertThat(slotInfoLogMessage.captured).isEqualTo("User info was successfully received from API")
            coVerifyOrder {
                mockUserCacher.getActual()
                mockUserService.getUser(userId)
                userController.storeUser(mockUser)
            }
        }

        @Test
        fun `get user from cache with error state`() = runTest {
            // given
            userController = spyk(userController)

            every {
                mockUserCacher.getActual()
            } returns null
            val exception = QonversionException(ErrorCode.BackendError)
            coEvery {
                mockUserService.getUser(userId)
            } throws exception

            every {
                mockUserCacher.getActual(CacheState.Error)
            } returns mockUser

            // when
            val result = userController.getUser()

            // then
            assertThat(result).isEqualTo(mockUser)
            verify(exactly = 0) {
                mockLogger.info(any())
                userController.storeUser(any())
            }

            coVerifyOrder {
                mockUserCacher.getActual()
                mockUserService.getUser(userId)
                mockLogger.error("Failed to get User from API", exception)
                mockUserCacher.getActual(CacheState.Error)
            }
        }

        @Test
        fun `user is missing`() = runTest {
            // given
            every {
                mockUserCacher.getActual()
            } returns null

            coEvery {
                mockUserService.getUser(userId)
            } throws QonversionException(ErrorCode.BackendError)

            every {
                mockUserCacher.getActual(CacheState.Error)
            } returns null

            // when and then
            coAssertThatQonversionExceptionThrown(ErrorCode.UserInfoIsMissing) {
                userController.getUser()
            }

            verify(exactly = 0) {
                mockLogger.info(any())
                userController.storeUser(any())
            }

            coVerifySequence {
                mockUserCacher.getActual()
                mockUserService.getUser(userId)
                mockUserCacher.getActual(CacheState.Error)
            }
        }
    }

    @Nested
    inner class StoreUserTest {
        @Test
        fun `store user successful`() {
            // given
            val mockUser = mockk<User>()
            val userSlot = slot<User>()
            every {
                mockUserCacher.store(capture(userSlot))
            } just Runs

            // when
            userController.storeUser(mockUser)

            // then
            verify(exactly = 1) { mockUserCacher.store(mockUser) }
            assertThat(userSlot.captured).isEqualTo(mockUser)
            assertThat(slotInfoLogMessage.captured).contains("User cache was successfully updated")
        }

        @Test
        fun `store user error`() {
            // given
            val exception = QonversionException(ErrorCode.Serialization, "error")
            every {
                mockUserCacher.store(mockUser)
            } throws exception

            // when and then
            assertDoesNotThrow {
                userController.storeUser(mockUser)
            }
            verify(exactly = 1) { mockLogger.error("Failed to update user cache", exception) }
        }
    }
}