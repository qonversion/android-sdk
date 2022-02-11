package com.qonversion.android.sdk.internal

import android.app.Application
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.Store
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.di.DependenciesAssembly
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
    private val mockShouldConsumePurchases = false
    private val mockBackgroundInternalCacheLifetime = mockk<InternalCacheLifetime>()
    private val mockPrimaryConfig =
        PrimaryConfig(mockProjectKey, mockLaunchMode, mockEnvironment)
    private val mockNetworkConfig = NetworkConfig()
    private val mockStoreConfig = StoreConfig(mockStore, mockShouldConsumePurchases)
    private val mockLoggerConfig = LoggerConfig(mockLogLevel, mockLogTag)
    private val mockInternalConfig = mockk<InternalConfig>(relaxed = true)
    private val mockDI = mockk<DependenciesAssembly>(relaxed = true)

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
            mockBackgroundCacheLifetime
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

            // when
            qonversionInternal = QonversionInternal(qonversionConfig, mockInternalConfig, mockDI)

            // then
            verify {
                mockInternalConfig.primaryConfig = mockPrimaryConfig
                mockInternalConfig.storeConfig = mockStoreConfig
                mockInternalConfig.networkConfig = mockNetworkConfig
                mockInternalConfig.loggerConfig = mockLoggerConfig
                mockInternalConfig.cacheLifetimeConfig = expectedCacheLifetimeConfig
            }
        }
    }

    @Nested
    inner class SettersTest {

        @BeforeEach
        fun setUp() {
            qonversionInternal = QonversionInternal(qonversionConfig, mockInternalConfig, mockDI)
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
            every { mockInternalConfig.cacheLifetimeConfig} returns mockCacheLifetimeConfig

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
    }
}
