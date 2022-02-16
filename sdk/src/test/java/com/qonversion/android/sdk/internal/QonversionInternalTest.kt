package com.qonversion.android.sdk.internal

import android.app.Application
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.Store
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.di.DependenciesAssembly
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.user.controller.UserController
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

internal class QonversionInternalTest {
    private lateinit var qonversionInternal: QonversionInternal
    private lateinit var qonversionConfig: QonversionConfig

    private val mockApplication = mockk<Application>()
    private val mockStore = mockk<Store>()
    private val mockProjectKey = "projectKey"
    private val mockLaunchMode = mockk<LaunchMode>()
    private val mockEnvironment = mockk<Environment>()
    private val mockLogLevel = mockk<LogLevel>()
    private val mockLogTag = "log tag"
    private val mockBackgroundCacheLifetime = mockk<CacheLifetime>()
    private val mockEntitlementsUpdateListener = mockk<EntitlementsUpdateListener>()
    private val mockShouldConsumePurchases = false
    private val mockBackgroundInternalCacheLifetime = mockk<InternalCacheLifetime>()
    private val mockPrimaryConfig =
        PrimaryConfig(mockProjectKey, mockLaunchMode, mockEnvironment)
    private val mockNetworkConfig = NetworkConfig()
    private val mockStoreConfig = StoreConfig(mockStore, mockShouldConsumePurchases)
    private val mockLoggerConfig = LoggerConfig(mockLogLevel, mockLogTag)
    private val mockInternalConfig = mockk<InternalConfig>(relaxed = true)
    private val mockDependenciesAssembly = mockk<DependenciesAssembly>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(InternalCacheLifetime)
        every {
            InternalCacheLifetime.from(mockBackgroundCacheLifetime)
        } returns mockBackgroundInternalCacheLifetime

        qonversionConfig = QonversionConfig(
            mockApplication,
            mockPrimaryConfig,
            mockStoreConfig,
            mockLoggerConfig,
            mockNetworkConfig,
            mockBackgroundCacheLifetime,
            mockEntitlementsUpdateListener
        )
    }

    @AfterEach
    fun afterEach() {
        unmockkObject(InternalCacheLifetime)
    }

    @Nested
    inner class InitTest {

        @Test
        fun `init`() {
            // given
            val expectedCacheLifetimeConfig = CacheLifetimeConfig(
                mockBackgroundInternalCacheLifetime,
                InternalCacheLifetime.FiveMin
            )
            val slotWeakReference = slot<WeakReference<EntitlementsUpdateListener?>>()
            every { mockInternalConfig.weakEntitlementsUpdateListener = capture(slotWeakReference) } just runs

            // when
            qonversionInternal =
                QonversionInternal(qonversionConfig, mockInternalConfig, mockDependenciesAssembly)

            // then
            verify {
                mockInternalConfig.primaryConfig = mockPrimaryConfig
                mockInternalConfig.storeConfig = mockStoreConfig
                mockInternalConfig.networkConfig = mockNetworkConfig
                mockInternalConfig.loggerConfig = mockLoggerConfig
                mockInternalConfig.cacheLifetimeConfig = expectedCacheLifetimeConfig
            }
            assertThat(slotWeakReference.captured.get()).isSameAs(mockEntitlementsUpdateListener)
        }
    }

    @Nested
    inner class SettersTest {

        @BeforeEach
        fun setUp() {
            qonversionInternal =
                QonversionInternal(qonversionConfig, mockInternalConfig, mockDependenciesAssembly)
        }

        @Test
        fun `set environment`() {
            // given
            val environments = Environment.values()
            every { mockInternalConfig.primaryConfig } returns mockPrimaryConfig

            environments.forEach { environment ->
                val expectedPrimaryConfig = mockPrimaryConfig.copy(environment = environment)

                // when
                qonversionInternal.setEnvironment(environment)

                // then
                verify { mockInternalConfig.primaryConfig = expectedPrimaryConfig }
            }
        }

        @Test
        fun `set log level`() {
            // given
            val logLevels = LogLevel.values()
            every { mockInternalConfig.loggerConfig } returns mockLoggerConfig

            logLevels.forEach { logLevel ->
                val expectedLoggerConfig = mockLoggerConfig.copy(logLevel = logLevel)

                // when
                qonversionInternal.setLogLevel(logLevel)

                // then
                verify { mockInternalConfig.loggerConfig = expectedLoggerConfig }
            }
        }

        @Test
        fun `set log tag`() {
            // given
            val logTag = "logTag"
            every { mockInternalConfig.loggerConfig } returns mockLoggerConfig
            val expectedLoggerConfig = mockLoggerConfig.copy(logTag = logTag)

            // when
            qonversionInternal.setLogTag(logTag)

            // then
            verify { mockInternalConfig.loggerConfig = expectedLoggerConfig }
        }

        @Test
        fun `set background cache lifetime`() {
            // given
            val mockForegroundInternalCacheLifetime = mockk<InternalCacheLifetime>()
            val mockCacheLifetimeConfig = CacheLifetimeConfig(
                mockBackgroundInternalCacheLifetime,
                mockForegroundInternalCacheLifetime
            )
            every { mockInternalConfig.cacheLifetimeConfig } returns mockCacheLifetimeConfig

            CacheLifetime.values().forEach { cacheLifetime ->
                val internalCacheLifetime = mockk<InternalCacheLifetime>()

                every {
                    InternalCacheLifetime.from(cacheLifetime)
                } returns internalCacheLifetime

                val expectedCacheLifetime = mockCacheLifetimeConfig.copy(
                    backgroundCacheLifetime = internalCacheLifetime
                )

                // when
                qonversionInternal.setCacheLifetime(cacheLifetime)

                // then
                verify { mockInternalConfig.cacheLifetimeConfig = expectedCacheLifetime }
            }
        }

        @Test
        fun `set entitlements listener`() {
            // given
            val newEntitlementsListener = mockk<EntitlementsUpdateListener>()
            val slotWeakReference = slot<WeakReference<EntitlementsUpdateListener?>>()
            every { mockInternalConfig.weakEntitlementsUpdateListener = capture(slotWeakReference) } just runs

            // when
            qonversionInternal.setEntitlementsUpdateListener(newEntitlementsListener)

            // then
            assertThat(slotWeakReference.captured.get()).isSameAs(newEntitlementsListener)
        }
    }

    @Nested
    inner class SetUserPropertiesTest {
        private val mockUserPropertiesController = mockk<UserPropertiesController>()

        @BeforeEach
        fun setUp() {
            every {
                mockDependenciesAssembly.userPropertiesController()
            } returns mockUserPropertiesController

            qonversionInternal =
                QonversionInternal(qonversionConfig, mockInternalConfig, mockDependenciesAssembly)
        }

        @Test
        fun `set all user properties`() {
            // given
            val userProperties = UserProperty.values()
            val value = "value"

            every {
                mockUserPropertiesController.setProperty(any(), value)
            } just runs

            userProperties.forEach { key ->
                // when
                qonversionInternal.setUserProperty(key, value)

                // then
                verify {
                    mockUserPropertiesController.setProperty(key.code, value)
                }
            }
        }

        @Test
        fun `set custom user property`() {
            // given
            val key = "key"
            val value = "value"
            every {
                mockUserPropertiesController.setProperty(key, value)
            } just runs

            // when
            qonversionInternal.setCustomUserProperty(key, value)

            // then
            verify {
                mockUserPropertiesController.setProperty(key, value)
            }
        }

        @Test
        fun `set user properties`() {
            // given
            val properties = mapOf("key" to "value")
            every {
                mockUserPropertiesController.setProperties(properties)
            } just runs

            // when
            qonversionInternal.setUserProperties(properties)

            // then
            verify {
                mockUserPropertiesController.setProperties(properties)
            }
        }
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class GetUserInfoTest {
        private val mockUserController = mockk<UserController>()

        @BeforeEach
        fun setUp() {
            every {
                mockDependenciesAssembly.userController()
            } returns mockUserController
        }

        @Test
        fun `get user info suspend`() = runTest {
            // given
            qonversionInternal =
                QonversionInternal(
                    qonversionConfig,
                    mockInternalConfig,
                    mockDependenciesAssembly
                )
            val mockUser = mockk<User>()
            coEvery { mockUserController.getUser() } returns mockUser

            // when
            val result = qonversionInternal.getUserInfo()

            // then
            assertThat(result).isEqualTo(mockUser)
            coVerify(exactly = 1) {
                mockUserController.getUser()
            }
        }

        @Test
        fun `get user info success callback`() = runTest {
            // given
            qonversionInternal = spyk(
                QonversionInternal(
                    qonversionConfig,
                    mockInternalConfig,
                    mockDependenciesAssembly,
                    this
                )
            )

            val mockUser = mockk<User>()
            coEvery { qonversionInternal.getUserInfo() } returns mockUser
            var isLambdaCalled = false

            // when and then
            qonversionInternal.getUserInfo(
                onSuccess = {
                    isLambdaCalled = true
                    assertThat(it).isEqualTo(mockUser)
                },
                onError = {
                    fail("Shouldn't go here")
                })

            yield()
            assertThat(isLambdaCalled).isTrue
            coVerify(exactly = 1) { qonversionInternal.getUserInfo() }
        }

        @Test
        fun `get user info error callback`() = runTest {
            // given
            qonversionInternal = spyk(
                QonversionInternal(
                    qonversionConfig,
                    mockInternalConfig,
                    mockDependenciesAssembly,
                    this
                )
            )
            val exception = QonversionException(ErrorCode.Serialization, "error")
            coEvery { qonversionInternal.getUserInfo() } throws exception
            var isLambdaCalled = false

            // when and then
            qonversionInternal.getUserInfo(
                onSuccess = {
                    fail("Shouldn't go here")
                },
                onError = {
                    isLambdaCalled = true
                    assertThat(it).isSameAs(exception)
                })

            yield()
            assertThat(isLambdaCalled).isTrue
            coVerify(exactly = 1) { qonversionInternal.getUserInfo() }
        }
    }
}
