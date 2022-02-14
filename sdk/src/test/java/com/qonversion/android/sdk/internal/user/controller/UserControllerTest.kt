package com.qonversion.android.sdk.internal.user.controller

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.CacheState
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.service.UserService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class UserControllerTest {

    private lateinit var userController: UserControllerImpl

    private val mockUserService = mockk<UserService>()
    private val mockUserCacher = mockk<Cacher<User>>()
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

    @Test
    suspend fun `get user from cache with default state`() {
        // given
        every {
            mockUserCacher.getActual(StorageConstants.UserInfo.key)
        } returns mockUser

        // when
        val result = userController.getUser()

        // then
        assertThat(result).isEqualTo(mockUser)
        assertThat(slotVerboseLogMessage.captured).isEqualTo("getUser() -> started")
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `get user from API successfully`() = runTest {
        // given
        userController = spyk(userController)

        every {
            mockUserCacher.getActual(StorageConstants.UserInfo.key)
        } returns null

        val userId = "userId"
        every {
            mockUserService.obtainUserId()
        } returns userId

        coEvery {
            mockUserService.getUser(userId)
        } returns mockUser

        every {
            userController.storeUser(mockUser)
        } just Runs

        // when
        val result = userController.getUser()

        // then
        assertThat(result).isEqualTo(mockUser)
        coVerifyOrder {
            mockUserCacher.getActual(StorageConstants.UserInfo.key)
            mockUserService.obtainUserId()
            mockUserService.getUser(userId)
            userController.storeUser(mockUser)
        }
    }

    @Test
    fun `store user success`() {
        // given
        val mockUser = mockk<User>()
        val userSlot = slot<User>()
        every {
            mockUserCacher.store(StorageConstants.UserInfo.key, capture(userSlot))
        } just Runs

        // when
        userController.storeUser(mockUser)

        // then
        assertThat(userSlot.captured).isEqualTo(mockUser)
    }

    @Test
    fun `store user error`() {
        // given
        val throwable = QonversionException(ErrorCode.Serialization, "error")
        every {
            mockUserCacher.store(StorageConstants.UserInfo.key, mockUser)
        } throws throwable

        // when and then
        assertDoesNotThrow {
            userController.storeUser(mockUser)
        }
    }

    @Test
    suspend fun `get user from cache with error state`() {
        // given
        every {
            mockUserCacher.getActual(StorageConstants.UserInfo.key)
        } returns null

        every {
            mockUserCacher.getActual(StorageConstants.UserInfo.key, CacheState.Error)
        } returns mockUser

        val userId = "userId"
        every {
            mockUserService.obtainUserId()
        } returns userId

        coEvery {
            mockUserService.getUser(userId)
        } throws  mockUser


        // when
        val result = userController.getUser()

        // then
        assertThat(result).isEqualTo(mockUser)
        assertThat(slotVerboseLogMessage.captured).isEqualTo("getUser() -> started")
    }
}
