package com.qonversion.android.sdk

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.Store
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.utils.isDebuggable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


internal class QonversionConfigTest {

    private val mockApplication = mockk<Application>()
    private val projectKey = "some project key"
    private val mockLaunchMode = mockk<LaunchMode>()
    private val mockStore = mockk<Store>()
    private val mockEnvironment = mockk<Environment>()
    private val mockLogLevel = mockk<LogLevel>()
    private val mockLogTag = "some tag"
    private val mockBackgroundCacheLifetime = mockk<CacheLifetime>()
    private val mockShouldConsumePurchases = true

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
    }

    @Nested
    inner class SettersTest {

        @Test
        fun `setting environment type`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setEnvironment(mockEnvironment)

            // then
            assertThat(builder.environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `setting log level`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setLogLevel(mockLogLevel)

            // then
            assertThat(builder.logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `setting log tag`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setLogTag(mockLogTag)

            // then
            assertThat(builder.logTag).isSameAs(mockLogTag)
        }

        @Test
        fun `setting should consume purchases`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
            builder.shouldConsumePurchases = true

            // when
            builder.setShouldConsumePurchases(false)

            // then
            assertThat(builder.shouldConsumePurchases).isEqualTo(false)
        }

        @Test
        fun `setting background cache lifetime`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setBackgroundCacheLifetime(mockBackgroundCacheLifetime)

            // then
            assertThat(builder.backgroundCacheLifetime).isSameAs(mockBackgroundCacheLifetime)
        }
    }

    @Nested
    inner class BuildMethodTest {

        @BeforeEach
        fun setUp() {
            mockkStatic("com.qonversion.android.sdk.internal.utils.ExtensionsKt")
        }

        @Test
        fun `successful build`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore).apply {
                environment = mockEnvironment
                logLevel = mockLogLevel
                logTag = mockLogTag
                backgroundCacheLifetime = mockBackgroundCacheLifetime
                shouldConsumePurchases = mockShouldConsumePurchases
            }
            val expResult = QonversionConfig(
                mockApplication,
                projectKey,
                mockLaunchMode,
                mockStore,
                mockEnvironment,
                mockLogLevel,
                mockLogTag,
                mockBackgroundCacheLifetime,
                mockShouldConsumePurchases
            )

            // when
            val result = builder.build()

            // then
            verify(exactly = 0) { Log.w(any(), any<String>()) }
            assertThat(result).isEqualToComparingFieldByField(expResult)
        }

        @Test
        fun `building sandbox config for release`() {
            // given
            val sandboxEnvironment = Environment.Sandbox
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore).apply {
                environment = sandboxEnvironment
                logLevel = mockLogLevel
                logTag = mockLogTag
                backgroundCacheLifetime = mockBackgroundCacheLifetime
                shouldConsumePurchases = mockShouldConsumePurchases
            }
            every { mockApplication.isDebuggable } returns false
            val expResult = QonversionConfig(
                mockApplication,
                projectKey,
                mockLaunchMode,
                mockStore,
                sandboxEnvironment,
                mockLogLevel,
                mockLogTag,
                mockBackgroundCacheLifetime,
                mockShouldConsumePurchases
            )
            val slotWarningMessage = slot<String>()
            every { Log.w(any(), capture(slotWarningMessage)) } returns 0

            // when
            val result = builder.build()

            // then
            assertThat(result).isEqualToComparingFieldByField(expResult)
            verify(exactly = 1) { Log.w(any(), any<String>()) }
            assertThat(slotWarningMessage.captured)
                .isEqualTo("Environment level is set to Sandbox for release build.")
        }

        @Test
        fun `building production config for debug`() {
            // given
            val prodEnvironment = Environment.Production
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore).apply {
                environment = prodEnvironment
                logLevel = mockLogLevel
                logTag = mockLogTag
                backgroundCacheLifetime = mockBackgroundCacheLifetime
                shouldConsumePurchases = mockShouldConsumePurchases
            }
            every { mockApplication.isDebuggable } returns true
            val expResult = QonversionConfig(
                mockApplication,
                projectKey,
                mockLaunchMode,
                mockStore,
                prodEnvironment,
                mockLogLevel,
                mockLogTag,
                mockBackgroundCacheLifetime,
                mockShouldConsumePurchases
            )
            val slotWarningMessage = slot<String>()
            every { Log.w(any(), capture(slotWarningMessage)) } returns 0

            // when
            val result = builder.build()

            // then
            assertThat(result).isEqualToComparingFieldByField(expResult)
            verify(exactly = 1) { Log.w(any(), any<String>()) }
            assertThat(slotWarningMessage.captured)
                .isEqualTo("Environment level is set to Production for debug build.")
        }

        @Test
        fun `building with blank project key`() {
            listOf("", "   ").forEach { projectKey ->
                // given
                val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

                // when and then
                assertThatQonversionExceptionThrown(ErrorCode.ConfigPreparation) {
                    builder.build()
                }
            }
        }

        @AfterEach
        fun after() {
            unmockkStatic("com.qonversion.android.sdk.internal.utils.ExtensionsKt")
        }
    }

    @AfterEach
    fun after() {
        unmockkStatic(Log::class)
    }
}
