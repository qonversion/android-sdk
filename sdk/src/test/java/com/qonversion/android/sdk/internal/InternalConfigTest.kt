package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.logger.LoggerConfig
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InternalConfigTest {
    @Nested
    inner class EnvironmentProvider {
        @Test
        fun `get environment`() {
            // given
            val mockEnvironment = Environment.Sandbox
            InternalConfig.environment = mockEnvironment

            // when
            val environment = InternalConfig.environment

            // then
            assertThat(environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `is sandbox when sandbox env`() {
            // given
            val mockEnvironment = Environment.Sandbox
            InternalConfig.environment = mockEnvironment

            // when
            val isSandbox = InternalConfig.isSandbox

            // then
            assertThat(isSandbox).isTrue()
        }

        @Test
        fun `is sandbox when prod env`() {
            // given
            val mockEnvironment = Environment.Production
            InternalConfig.environment = mockEnvironment

            // when
            val isSandbox = InternalConfig.isSandbox

            // then
            assertThat(isSandbox).isFalse()
        }
    }

    @Nested
    inner class LoggerConfigProvider {
        @Test
        fun `get logger config`() {
            // given
            val mockLoggerConfig = mockk<LoggerConfig>()
            InternalConfig.loggerConfig = mockLoggerConfig

            // when
            val loggerConfig = InternalConfig.loggerConfig

            // then
            assertThat(loggerConfig).isSameAs(mockLoggerConfig)
        }
    }

    @Nested
    inner class CacheLifetimeConfigProvider {
        @Test
        fun `get cache lifetime config`() {
            // given
            val mockCacheLifetimeLoggerConfig = mockk<CacheLifetimeConfig>()
            InternalConfig.cacheLifetimeConfig = mockCacheLifetimeLoggerConfig

            // when
            val cacheLifetimeConfig = InternalConfig.cacheLifetimeConfig

            // then
            assertThat(cacheLifetimeConfig).isSameAs(mockCacheLifetimeLoggerConfig)
        }
    }
}