package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
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

    private val mockProjectKey = "projectKey"
    private val mockLaunchMode = mockk<LaunchMode>()
    private val mockEnvironment = mockk<Environment>()
    private val mockLogLevel = mockk<LogLevel>()
    private val mockLogTag = "log tag"
    private val mockBackgroundCacheLifetime = mockk<CacheLifetime>()
    private val mockShouldConsumePurchases = false
    private val mockBackgroundInternalCacheLifetime = mockk<InternalCacheLifetime>()

    @Nested
    inner class InitTest {
        private val mockConfig = mockk<QonversionConfig>()

        @BeforeEach
        fun setUp() {
            every { mockConfig.projectKey } returns mockProjectKey
            every { mockConfig.launchMode } returns mockLaunchMode
            every { mockConfig.environment } returns mockEnvironment
            every { mockConfig.backgroundCacheLifetime } returns mockBackgroundCacheLifetime

            mockkObject(InternalCacheLifetime)
            every {
                InternalCacheLifetime.from(mockBackgroundCacheLifetime)
            } returns mockBackgroundInternalCacheLifetime

            every { mockConfig.logLevel } returns mockLogLevel
            every { mockConfig.logTag } returns mockLogTag
            every { mockConfig.shouldConsumePurchases } returns mockShouldConsumePurchases
        }

        @Test
        fun `init`() {
            // given
            val expectedCacheLifetimeConfig = CacheLifetimeConfig(
                mockBackgroundInternalCacheLifetime,
                InternalCacheLifetime.FiveMin
            )
            val expectedLoggerConfig = LoggerConfig(mockLogLevel, mockLogTag)

            // when
            qonversionInternal = QonversionInternal(mockConfig)

            // then
            assertThat(InternalConfig.projectKey).isSameAs(mockProjectKey)
            assertThat(InternalConfig.launchMode).isSameAs(mockLaunchMode)
            assertThat(InternalConfig.environment).isSameAs(mockEnvironment)
            assertThat(InternalConfig.cacheLifetimeConfig).isEqualToComparingFieldByField(
                expectedCacheLifetimeConfig
            )
            assertThat(InternalConfig.loggerConfig).isEqualToComparingFieldByField(
                expectedLoggerConfig
            )
            assertThat(InternalConfig.shouldConsumePurchases).isSameAs(mockShouldConsumePurchases)
        }
    }

    @Nested
    inner class SettersTest {
        private val mockConfig = mockk<QonversionConfig>(relaxed = true)

        @BeforeEach
        fun setUp() {
            mockkObject(InternalCacheLifetime)
            every {
                InternalCacheLifetime.from(mockBackgroundCacheLifetime)
            } returns mockBackgroundInternalCacheLifetime

            qonversionInternal = QonversionInternal(mockConfig)
        }

        @Test
        fun `set environment`() {
            // given

            // when
            qonversionInternal.setEnvironment(mockEnvironment)

            // then
            assertThat(InternalConfig.environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `set log level`() {
            // given
            InternalConfig.loggerConfig = LoggerConfig(logTag = mockLogTag)

            // when
            qonversionInternal.setLogLevel(mockLogLevel)

            // then
            assertThat(InternalConfig.loggerConfig.logTag).isSameAs(mockLogTag)
            assertThat(InternalConfig.loggerConfig.logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `set log tag`() {
            // given
            InternalConfig.loggerConfig = LoggerConfig(logLevel = mockLogLevel)

            // when
            qonversionInternal.setLogTag(mockLogTag)

            // then
            assertThat(InternalConfig.loggerConfig.logTag).isSameAs(mockLogTag)
            assertThat(InternalConfig.loggerConfig.logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `set background cache lifetime`() {
            // given
            val foregroundCacheLifetime = mockk<InternalCacheLifetime>()
            InternalConfig.cacheLifetimeConfig =
                CacheLifetimeConfig(foregroundCacheLifetime = foregroundCacheLifetime)

            // when
            qonversionInternal.setBackgroundCacheLifetime(mockBackgroundCacheLifetime)

            // then
            assertThat(InternalConfig.cacheLifetimeConfig.foregroundCacheLifetime).isSameAs(
                foregroundCacheLifetime
            )
            assertThat(InternalConfig.cacheLifetimeConfig.backgroundCacheLifetime).isSameAs(
                mockBackgroundInternalCacheLifetime
            )
        }
    }
}

