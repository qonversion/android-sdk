package com.qonversion.android.sdk.internal.user.controller

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.dto.Entitlement
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.cache.CacheState
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.EntitlementsUpdateListenerProvider
import com.qonversion.android.sdk.internal.user.generator.UserIdGenerator
import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@ExperimentalCoroutinesApi
internal class UserControllerTest {

    private lateinit var userController: UserControllerImpl

    private val mockUserDataStorage = mockk<UserDataStorage>()
    private val mockUserIdGenerator = mockk<UserIdGenerator>()
    private val mockUserService = mockk<UserService>()
    private val mockUserCacher = mockk<Cacher<User?>>()
    private val mockEntitlementsUpdateListenerProvider = mockk<EntitlementsUpdateListenerProvider>(relaxed = true)
    private val mockAppLifecycleObserver = mockk<AppLifecycleObserver>()
    private val mockLogger = mockk<Logger>()

    private val testUserId = "test user id"
    private val slotInfoLogMessage = slot<String>()
    private val slotErrorLogMessage = slot<String>()
    private val slotVerboseLogMessage = slot<String>()
    private val mockUser = mockk<User>()

    @BeforeEach
    fun setUp() {
        every { mockUserDataStorage.getUserId() } returns testUserId
        every { mockAppLifecycleObserver.addListener(any()) } just runs

        userController = UserControllerImpl(
            mockUserService,
            mockUserCacher,
            mockUserDataStorage,
            mockEntitlementsUpdateListenerProvider,
            mockUserIdGenerator,
            mockAppLifecycleObserver,
            mockLogger
        )

        clearMocks(mockUserDataStorage)

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
            every { mockUserDataStorage.getUserId() } returns testUserId

            // when
            userController = createController()

            // then
            verify {
                mockAppLifecycleObserver.addListener(userController)
                mockUserDataStorage.getUserId()
            }
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
            userController = createController()

            // then
            verifyOrder {
                mockAppLifecycleObserver.addListener(userController)
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
            userController = createController()

            // then
            verifyOrder {
                mockAppLifecycleObserver.addListener(userController)
                mockUserDataStorage.getUserId()
                mockUserIdGenerator.generate()
                mockUserDataStorage.setOriginalUserId(testUserId)
            }
        }

        private fun createController() = UserControllerImpl(
            mockUserService,
            mockUserCacher,
            mockUserDataStorage,
            mockEntitlementsUpdateListenerProvider,
            mockUserIdGenerator,
            mockAppLifecycleObserver,
            mockLogger
        )
    }

    @Nested
    inner class GetUserTest {
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
            verify {
                mockUserService wasNot called
            }
        }

        @Test
        fun `get user from API successfully`() = runTest {
            // given
            userController = spyk(userController)

            every {
                mockUserCacher.getActual()
            } returns null

            every { mockUserDataStorage.requireUserId() } returns testUserId

            coEvery {
                mockUserService.getUser(testUserId)
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
                mockUserDataStorage.requireUserId()
                mockUserService.getUser(testUserId)
                userController.storeUser(mockUser)
            }
        }

        @Test
        fun `get user from cache due to service exception`() = runTest {
            // given
            userController = spyk(userController)
            val exception = QonversionException(ErrorCode.BackendError)

            every { mockUserCacher.getActual() } returns null
            every { mockUserDataStorage.requireUserId() } returns testUserId
            coEvery { mockUserService.getUser(testUserId) } throws exception
            every { mockUserCacher.getActual(CacheState.Error) } returns mockUser

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
                mockUserDataStorage.requireUserId()
                mockUserService.getUser(testUserId)
                mockLogger.error("Failed to get User from API", exception)
                mockUserCacher.getActual(CacheState.Error)
            }
        }

        @Test
        fun `get user from cache due to user id exception`() = runTest {
            // given
            userController = spyk(userController)
            val exception = QonversionException(ErrorCode.UserNotFound)

            every { mockUserCacher.getActual() } returns null
            every { mockUserDataStorage.requireUserId() } throws exception
            every { mockUserCacher.getActual(CacheState.Error) } returns mockUser

            // when
            val result = userController.getUser()

            // then
            assertThat(result).isEqualTo(mockUser)
            coVerify(exactly = 0) {
                mockLogger.info(any())
                userController.storeUser(any())
                mockUserService.getUser(any())
            }

            coVerifyOrder {
                mockUserCacher.getActual()
                mockUserDataStorage.requireUserId()
                mockLogger.error("Failed to get User from API", exception)
                mockUserCacher.getActual(CacheState.Error)
            }
        }

        @Test
        fun `user is missing`() = runTest {
            // given
            every { mockUserCacher.getActual() } returns null
            every { mockUserDataStorage.requireUserId() } returns testUserId
            coEvery {
                mockUserService.getUser(testUserId)
            } throws QonversionException(ErrorCode.BackendError)
            every { mockUserCacher.getActual(CacheState.Error) } returns null

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
                mockUserDataStorage.requireUserId()
                mockUserService.getUser(testUserId)
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

            every {
                mockUserCacher.store(any())
            } just runs

            // when
            userController.storeUser(mockUser)

            // then
            verify(exactly = 1) { mockUserCacher.store(mockUser) }
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

    @Nested
    inner class OnAppForegroundTest {

        @Test
        fun `not the first foreground`() {
            // given

            // when
            userController.onAppForeground(false)

            // then
            verify {
                mockUserCacher wasNot called
                mockUserService wasNot called
            }
        }

        @Test
        fun `actual cache exists`() = runTest {
            // given
            userController = createController(this)
            every { mockUserCacher.getActual() } returns mockUser

            // when
            userController.onAppForeground(true)

            // then
            yield()
            verify { mockUserCacher.getActual() }
            verify(exactly = 0) {
                mockUserCacher.store(any())
                mockUserService wasNot called
            }
        }

        @Test
        fun `actual cache does not exist`() = runTest {
            // given
            userController = spyk(createController(this))
            every { mockUserCacher.getActual() } returns null
            every { mockUserDataStorage.requireUserId() } returns testUserId
            coEvery { mockUserService.getUser(testUserId) } returns mockUser
            coEvery { userController.handleNewUserInfo(mockUser) } just runs

            // when
            userController.onAppForeground(true)

            // then
            yield()
            coVerifyOrder {
                mockUserCacher.getActual()
                mockUserDataStorage.requireUserId()
                mockUserService.getUser(testUserId)
                userController.handleNewUserInfo(mockUser)
            }
        }

        @Test
        fun `cacher throws exception`() = runTest {
            // given
            val exception = QonversionException(ErrorCode.BackendError)
            userController = spyk(createController(this))
            every { mockUserCacher.getActual() } throws exception
            coEvery { userController.handleNewUserInfo(mockUser) } just runs

            // when
            assertDoesNotThrow {
                userController.onAppForeground(true)
            }

            // then
            yield()
            verify {
                mockUserCacher.getActual()
                mockLogger.error("Requesting user on app first foreground failed", exception)
            }
            coVerify(exactly = 0) {
                mockUserDataStorage wasNot called
                mockUserService wasNot called
                userController.handleNewUserInfo(mockUser)
            }
        }
    }

    @Nested
    inner class HandleNewUserInfoTest {

        private val mockStoredUser = mockk<User>()
        private val mockEntitlements = mockk<List<Entitlement>>()
        private val mainDispatcher = StandardTestDispatcher()

        @BeforeEach
        fun setUp() {
            Dispatchers.setMain(mainDispatcher)

            every { mockUser.entitlements } returns mockEntitlements
        }

        @AfterEach
        fun tearDown() {
            Dispatchers.resetMain()
        }

        @Test
        fun `user entitlements does not differ`() = runTest {
            // given
            userController = spyk(createController(this))
            every { userController.storeUser(mockUser) } just runs
            every { mockUserCacher.get() } returns mockStoredUser
            every { mockStoredUser.entitlements } returns mockEntitlements

            // when
            userController.handleNewUserInfo(mockUser)

            // then
            verify {
                mockUserCacher.get()
                userController.storeUser(mockUser)
                mockEntitlementsUpdateListenerProvider wasNot called
            }
        }

        @Test
        fun `stored user does not exist`() = runTest {
            // given
            userController = spyk(createController(this))
            every { userController.storeUser(mockUser) } just runs
            every { mockUserCacher.get() } returns null

            // when
            userController.handleNewUserInfo(mockUser)

            // then
            verify {
                mockUserCacher.get()
                userController.storeUser(mockUser)
                mockEntitlementsUpdateListenerProvider
                    .entitlementsUpdateListener
                    ?.onEntitlementsUpdated(mockEntitlements)
            }
        }

        @Test
        fun `user entitlements differ`() = runTest {
            // given
            userController = spyk(createController(this))
            every { userController.storeUser(mockUser) } just runs
            every { mockUserCacher.get() } returns mockStoredUser
            every { mockStoredUser.entitlements } returns emptyList()

            // when
            userController.handleNewUserInfo(mockUser)

            // then
            verify {
                mockUserCacher.get()
                userController.storeUser(mockUser)
                mockEntitlementsUpdateListenerProvider
                    .entitlementsUpdateListener
                    ?.onEntitlementsUpdated(mockEntitlements)
            }
        }
    }

    private fun createController(scope: CoroutineScope): UserControllerImpl {
        every { mockUserDataStorage.getUserId() } returns testUserId

        val controller = UserControllerImpl(
            mockUserService,
            mockUserCacher,
            mockUserDataStorage,
            mockEntitlementsUpdateListenerProvider,
            mockUserIdGenerator,
            mockAppLifecycleObserver,
            mockLogger,
            scope
        )

        clearMocks(mockUserDataStorage)

        return controller
    }
}
