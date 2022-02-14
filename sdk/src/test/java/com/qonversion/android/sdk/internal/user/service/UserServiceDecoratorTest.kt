package com.qonversion.android.sdk.internal.user.service

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class UserServiceDecoratorTest {

    private val mockUserService = mockk<UserService>()
    private val mockUser = mockk<User>()
    private lateinit var userServiceDecorator: UserServiceDecorator

    @BeforeEach
    fun setUp() {
        userServiceDecorator = UserServiceDecorator(mockUserService)
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class GetUserTest {

        private val testUserId = "test id"

        @Test
        fun `user exists`() = runTest {
            // given
            coEvery { mockUserService.getUser(testUserId) } returns mockUser

            // when
            val user = userServiceDecorator.getUser(testUserId)

            // then
            coVerify { mockUserService.getUser(testUserId) }
            coVerify(exactly = 0) { mockUserService.createUser(any()) }
            assertThat(user).isSameAs(mockUser)
        }

        @Test
        fun `user does not exist`() = runTest {
            // given
            coEvery {
                mockUserService.getUser(testUserId)
            } throws QonversionException(ErrorCode.UserNotFound)
            coEvery { mockUserService.createUser(testUserId) } returns mockUser

            // when
            val user = userServiceDecorator.getUser(testUserId)

            // then
            coVerifyOrder {
                mockUserService.getUser(testUserId)
                mockUserService.createUser(testUserId)
            }
            assertThat(user).isSameAs(mockUser)
        }

        @Test
        fun `backed error occurred`() = runTest {
            // given
            val exception = QonversionException(ErrorCode.BackendError)
            coEvery { mockUserService.getUser(testUserId) } throws exception

            // when
            val e = coAssertThatQonversionExceptionThrown {
                userServiceDecorator.getUser(testUserId)
            }

            // then
            assertThat(e).isSameAs(exception)
            coVerify { mockUserService.getUser(testUserId) }
            coVerify(exactly = 0) { mockUserService.createUser(any()) }
        }
    }
}
