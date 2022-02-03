package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.provider.NetworkConfigHolder
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.LoggerConfigProvider
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InternalConfigTest {
    @Nested
    inner class EnvironmentProviderTest {
        private val mockProjectKey = "projectKey"
        private val mockLaunchMode = mockk<LaunchMode>()
        private val mockEnvironment = mockk<Environment>()

        @Test
        fun `get environment`() {
            // given
            val environmentProvider: EnvironmentProvider = InternalConfig
            val mockPrimaryConfig = PrimaryConfig(mockProjectKey, mockLaunchMode, mockEnvironment)
            InternalConfig.primaryConfig = mockPrimaryConfig

            // when
            val environment = environmentProvider.environment

            // then
            assertThat(environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `is sandbox when sandbox env`() {
            // given
            val environmentProvider: EnvironmentProvider = InternalConfig
            val mockEnvironment = Environment.Sandbox
            InternalConfig.primaryConfig =
                PrimaryConfig(mockProjectKey, mockLaunchMode, mockEnvironment)

            // when
            val isSandbox = environmentProvider.isSandbox

            // then
            assertThat(isSandbox).isTrue
        }

        @Test
        fun `is not sandbox when prod env`() {
            // given
            val environmentProvider: EnvironmentProvider = InternalConfig
            val mockEnvironment = Environment.Production
            InternalConfig.primaryConfig =
                PrimaryConfig(mockProjectKey, mockLaunchMode, mockEnvironment)

            // when
            val isSandbox = environmentProvider.isSandbox

            // then
            assertThat(isSandbox).isFalse
        }
    }

    @Nested
    inner class LoggerConfigProviderTest {
        private val mockLogLevel = mockk<LogLevel>()
        private val mockLogTag = "logTag"
        private val mockLoggerConfig = LoggerConfig(mockLogLevel, mockLogTag)

        @Test
        fun `get log level`() {
            // given
            val loggerConfigProvider: LoggerConfigProvider = InternalConfig
            InternalConfig.loggerConfig = mockLoggerConfig

            // when
            val logLevel = loggerConfigProvider.logLevel

            // then
            assertThat(logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `get log tag`() {
            // given
            val loggerConfigProvider: LoggerConfigProvider = InternalConfig
            InternalConfig.loggerConfig = mockLoggerConfig

            // when
            val logTag = loggerConfigProvider.logTag

            // then
            assertThat(logTag).isSameAs(mockLogTag)
        }
    }

    @Nested
    inner class CacheLifetimeConfigProviderTest {
        private val mockCacheLifetimeLoggerConfig = mockk<CacheLifetimeConfig>()

        @Test
        fun `get cache lifetime config`() {
            // given
            val cacheLifetimeConfigProvider: CacheLifetimeConfigProvider = InternalConfig
            InternalConfig.cacheLifetimeConfig = mockCacheLifetimeLoggerConfig

            // when
            val cacheLifetimeConfig = cacheLifetimeConfigProvider.cacheLifetimeConfig

            // then
            assertThat(cacheLifetimeConfig).isSameAs(mockCacheLifetimeLoggerConfig)
        }
    }

    @Nested
    inner class NetworkConfigHolderTest {
        private val mockCanSendRequests = true

        @Test
        fun `get canSendRequests`() {
            // given
            val networkConfigHolder: NetworkConfigHolder = InternalConfig
            InternalConfig.networkConfig = NetworkConfig(mockCanSendRequests)

            // when
            val canSendRequests = networkConfigHolder.canSendRequests

            // then
            assertThat(canSendRequests).isSameAs(mockCanSendRequests)
        }

        @Test
        fun `set canSendRequests`() {
            // given
            val networkConfigHolder: NetworkConfigHolder = InternalConfig
            InternalConfig.networkConfig = NetworkConfig()

            // when
            networkConfigHolder.canSendRequests = mockCanSendRequests

            // then
            assertThat(InternalConfig.networkConfig.canSendRequests).isSameAs(mockCanSendRequests)
        }
    }
}