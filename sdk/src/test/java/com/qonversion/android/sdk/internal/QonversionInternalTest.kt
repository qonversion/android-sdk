package com.qonversion.android.sdk.internal

import android.app.Application
import com.qonversion.android.sdk.QonversionConfig
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
import com.qonversion.android.sdk.internal.logger.LoggerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.assertj.core.api.Assertions.assertThat
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
            qonversionInternal = QonversionInternal(qonversionConfig)

            // then
            assertThat(InternalConfig.primaryConfig).isSameAs(qonversionConfig.primaryConfig)
            assertThat(InternalConfig.storeConfig).isSameAs(qonversionConfig.storeConfig)
            assertThat(InternalConfig.networkConfig).isSameAs(qonversionConfig.networkConfig)
            assertThat(InternalConfig.loggerConfig).isSameAs(qonversionConfig.loggerConfig)
            assertThat(InternalConfig.cacheLifetimeConfig).isEqualToComparingFieldByField(
                expectedCacheLifetimeConfig
            )
        }
    }

    @Nested
    inner class SettersTest {

        @BeforeEach
        fun setUp() {
            qonversionInternal = QonversionInternal(qonversionConfig)
        }

        @Test
        fun `set environment`() {
            // given
            val environments = Environment.values()

            // when
            environments.forEach { item ->
                qonversionInternal.setEnvironment(item)
                // then
                assertThat(InternalConfig.primaryConfig.environment).isSameAs(item)
                assertThat(InternalConfig.primaryConfig.projectKey).isSameAs(mockPrimaryConfig.projectKey)
                assertThat(InternalConfig.primaryConfig.launchMode).isSameAs(mockPrimaryConfig.launchMode)
                assertThat(InternalConfig.primaryConfig.sdkVersion).isSameAs(mockPrimaryConfig.sdkVersion)
            }
        }

        @Test
        fun `set log level`() {
            // given
            val logLevels = LogLevel.values()


            logLevels.forEach { item ->
                // when
                qonversionInternal.setLogLevel(item)
                // then
                assertThat(InternalConfig.loggerConfig.logTag).isSameAs(mockLogTag)
                assertThat(InternalConfig.loggerConfig.logLevel).isSameAs(item)
            }
        }

        @Test
        fun `set log tag`() {
            // given
            val logTag = "logTag"

            // when
            qonversionInternal.setLogTag(logTag)

            // then
            assertThat(InternalConfig.loggerConfig.logTag).isSameAs(logTag)
            assertThat(InternalConfig.loggerConfig.logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `set background cache lifetime`() {
            // given
            val mockForegroundInternalCacheLifetime = mockk<InternalCacheLifetime>()
            val mockCacheLifetimeConfig = CacheLifetimeConfig(
                mockBackgroundInternalCacheLifetime,
                mockForegroundInternalCacheLifetime
            )
            InternalConfig.cacheLifetimeConfig = mockCacheLifetimeConfig

            val cacheLifetimeConfigs = CacheLifetime.values()

            cacheLifetimeConfigs.forEach { item ->
                val itemInternalCacheLifetime = mockk<InternalCacheLifetime>()

                every {
                    InternalCacheLifetime.from(item)
                } returns itemInternalCacheLifetime

                // when
                qonversionInternal.setCacheLifetime(item)

                // then
                assertThat(InternalConfig.cacheLifetimeConfig.foregroundCacheLifetime).isSameAs(
                    mockForegroundInternalCacheLifetime
                )
                assertThat(InternalConfig.cacheLifetimeConfig.backgroundCacheLifetime).isSameAs(
                    itemInternalCacheLifetime
                )
            }
        }
    }
}

