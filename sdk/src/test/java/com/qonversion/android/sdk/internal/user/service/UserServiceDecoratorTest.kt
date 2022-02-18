package com.qonversion.android.sdk.internal.user.service

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
internal class UserServiceDecoratorTest {

    private val mockUserService = mockk<UserService>()
    private val mockUser = mockk<User>()
    private val testUserId = "test id"
    private lateinit var userServiceDecorator: UserServiceDecorator

    @BeforeEach
    fun setUp() {
        userServiceDecorator = UserServiceDecorator(mockUserService)
    }

    @Nested
    inner class GetUserTest {

        private val mockUserDeferred = mockk<CompletableDeferred<User>>(relaxed = true)

        @BeforeEach
        fun setUp() {
            userServiceDecorator = spyk(userServiceDecorator)
        }

        @Test
        fun `no concurrent request exists`() = runTest {
            // given
            val loadingTime = 100_000L
            userServiceDecorator.userLoadingDeferred = null
            coEvery { userServiceDecorator.loadOrCreateUser(testUserId) } coAnswers {
                delay(loadingTime)
                mockUser
            }

            // when
            launch {
                val result = userServiceDecorator.getUser(testUserId)
                verify { mockUserDeferred.complete(mockUser) }
                assertThat(userServiceDecorator.userLoadingDeferred).isNull()
                assertThat(result).isSameAs(mockUser)
            }

            // then
            yield()
            assertThat(userServiceDecorator.userLoadingDeferred).isNotNull
            coVerify { userServiceDecorator.loadOrCreateUser(testUserId) }

            // replace deferred with mock one to be able to verify `complete` call
            userServiceDecorator.userLoadingDeferred = mockUserDeferred

        }

        @Test
        fun `concurrent request exists`() = runTest {
            // given
            userServiceDecorator.userLoadingDeferred = mockUserDeferred
            coEvery { mockUserDeferred.await() } returns mockUser

            // when
            val result = userServiceDecorator.getUser(testUserId)

            // then
            coVerify(exactly = 0) { userServiceDecorator.loadOrCreateUser(any()) }
            assertThat(result).isSameAs(mockUser)
        }
    }

    @Nested
    inner class LoadOrCreateUserTest {

        @Test
        fun `user exists`() = runTest {
            // given
            coEvery { mockUserService.getUser(testUserId) } returns mockUser

            // when
            val user = userServiceDecorator.loadOrCreateUser(testUserId)

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
            val user = userServiceDecorator.loadOrCreateUser(testUserId)

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
                userServiceDecorator.loadOrCreateUser(testUserId)
            }

            // then
            assertThat(e).isSameAs(exception)
            coVerify { mockUserService.getUser(testUserId) }
            coVerify(exactly = 0) { mockUserService.createUser(any()) }
        }
    }
}
