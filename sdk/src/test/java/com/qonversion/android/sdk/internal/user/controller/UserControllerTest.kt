package com.qonversion.android.sdk.internal.user.controller

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.CacheState
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.service.UserService
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

    private val mockUserService = mockk<UserService>()
    private val mockUserCacher = mockk<Cacher<User?>>()
    private val mockLogger = mockk<Logger>()

    private val slotInfoLogMessage = slot<String>()
    private val slotErrorLogMessage = slot<String>()
    private val slotVerboseLogMessage = slot<String>()
    private val mockUser = mockk<User>()

    @BeforeEach
    fun setUp() {
        userController = UserControllerImpl(mockUserService, mockUserCacher, mockLogger)

        every { mockLogger.info(capture(slotInfoLogMessage)) } just runs
        every { mockLogger.error(capture(slotErrorLogMessage)) } just runs
        every { mockLogger.error(capture(slotErrorLogMessage), any()) } just runs
        every { mockLogger.verbose(capture(slotVerboseLogMessage)) } just runs
    }

    @Nested
    @ExperimentalCoroutinesApi
    inner class GetUserTest {
        private val userId = "userId"

        @BeforeEach
        fun setUp() {
            every {
                mockUserService.obtainUserId()
            } returns userId
        }

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
                mockUserService.obtainUserId()
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

            coEvery {
                mockUserService.getUser(userId)
            } throws QonversionException(ErrorCode.BackendError)

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

            coVerifySequence {
                mockUserCacher.getActual()
                mockUserService.obtainUserId()
                mockUserService.getUser(userId)
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
            val e = coAssertThatQonversionExceptionThrown(ErrorCode.UserInfoIsMissing) {
                userController.getUser()
            }
            assertThat(e.message).contains("Failed to retrieve User info")

            verify(exactly = 0) {
                mockLogger.info(any())
                userController.storeUser(any())
            }

            coVerifySequence {
                mockUserCacher.getActual()
                mockUserService.obtainUserId()
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
            assertThat(slotInfoLogMessage.captured).contains("Cache with user was successfully updated")
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
