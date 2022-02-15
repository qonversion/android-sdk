package com.qonversion.android.sdk

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.Store
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.utils.isDebuggable
import com.qonversion.android.sdk.listeners.EntitlementsListener
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
    private val mockEntitlementsListener = mockk<EntitlementsListener>()
    private val mockShouldConsumePurchases = true
    private val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, mockEnvironment)
    private val mockNetworkConfig = NetworkConfig()
    private val mockStoreConfig = StoreConfig(mockStore, mockShouldConsumePurchases)
    private val mockLoggerConfig = LoggerConfig(mockLogLevel, mockLogTag)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
    }

    @Nested
    inner class SettersTest {

        @Test
        fun `setting environment type`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setEnvironment(mockEnvironment)

            // then
            assertThat(builder.environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `setting log level`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setLogLevel(mockLogLevel)

            // then
            assertThat(builder.logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `setting log tag`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setLogTag(mockLogTag)

            // then
            assertThat(builder.logTag).isSameAs(mockLogTag)
        }

        @Test
        fun `setting should consume purchases`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
            builder.shouldConsumePurchases = true

            // when
            builder.setShouldConsumePurchases(false)

            // then
            assertThat(builder.shouldConsumePurchases).isEqualTo(false)
        }

        @Test
        fun `setting entitlements listener`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
            builder.entitlementsListener = null

            // when
            builder.setEntitlementsListener(mockEntitlementsListener)

            // then
            assertThat(builder.entitlementsListener).isSameAs(mockEntitlementsListener)
        }

        @Test
        fun `setting background cache lifetime`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setCacheLifetime(mockBackgroundCacheLifetime)

            // then
            assertThat(builder.cacheLifetime).isSameAs(mockBackgroundCacheLifetime)
        }
    }

    @Nested
    inner class BuildMethodTest {

        @BeforeEach
        fun setUp() {
            mockkStatic("com.qonversion.android.sdk.internal.utils.ExtensionsKt")
        }

        @Test
        fun `successful build with full list of arguments`() {
            // given
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = mockEnvironment
                        logLevel = mockLogLevel
                        logTag = mockLogTag
                        cacheLifetime = mockBackgroundCacheLifetime
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsListener = mockEntitlementsListener
                    }
            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockLoggerConfig,
                mockNetworkConfig,
                mockBackgroundCacheLifetime,
                mockEntitlementsListener
            )

            // when
            val result = builder.build()

            // then
            verify(exactly = 0) { Log.w(any(), any<String>()) }
            assertThat(result).isEqualToComparingFieldByField(expResult)
        }

        @Test
        fun `successful build without full list of arguments`() {
            // given
            val defaultStore = Store.GooglePlay
            val defaultLogLevel = LogLevel.Info
            val defaultLogTag = "Qonversion"
            val defaultBackgroundCacheLifetime = CacheLifetime.ThreeDays
            val defaultEntitlementsListener: EntitlementsListener? = null
            val defaultShouldConsumePurchases = true
            val defaultEnvironment = Environment.Production
            val defaultCanSendRequests = true

            val mockApplication = mockk<Application>(relaxed = true)

            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode)

            val expPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, defaultEnvironment)
            val expStoreConfig = StoreConfig(defaultStore, defaultShouldConsumePurchases)
            val expLoggerConfig = LoggerConfig(defaultLogLevel, defaultLogTag)
            val expNetworkConfig = NetworkConfig(defaultCanSendRequests)
            val expResult = QonversionConfig(
                mockApplication,
                expPrimaryConfig,
                expStoreConfig,
                expLoggerConfig,
                expNetworkConfig,
                defaultBackgroundCacheLifetime,
                defaultEntitlementsListener
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
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = sandboxEnvironment
                        logLevel = mockLogLevel
                        logTag = mockLogTag
                        cacheLifetime = mockBackgroundCacheLifetime
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsListener = mockEntitlementsListener
                    }
            every { mockApplication.isDebuggable } returns false
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, sandboxEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockLoggerConfig,
                mockNetworkConfig,
                mockBackgroundCacheLifetime,
                mockEntitlementsListener
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
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = prodEnvironment
                        logLevel = mockLogLevel
                        logTag = mockLogTag
                        cacheLifetime = mockBackgroundCacheLifetime
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsListener = mockEntitlementsListener
                    }
            every { mockApplication.isDebuggable } returns true
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, prodEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockLoggerConfig,
                mockNetworkConfig,
                mockBackgroundCacheLifetime,
                mockEntitlementsListener
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
        fun `building sandbox config for debug`() {
            // given
            val sandboxEnvironment = Environment.Sandbox
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = sandboxEnvironment
                        logLevel = mockLogLevel
                        logTag = mockLogTag
                        cacheLifetime = mockBackgroundCacheLifetime
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsListener = mockEntitlementsListener
                    }
            every { mockApplication.isDebuggable } returns true
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, sandboxEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockLoggerConfig,
                mockNetworkConfig,
                mockBackgroundCacheLifetime,
                mockEntitlementsListener
            )

            // when
            val result = builder.build()

            // then
            assertThat(result).isEqualToComparingFieldByField(expResult)
            verify(exactly = 0) { Log.w(any(), any<String>()) }
        }

        @Test
        fun `building production config for release`() {
            // given
            val prodEnvironment = Environment.Production
            val builder =
                QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = prodEnvironment
                        logLevel = mockLogLevel
                        logTag = mockLogTag
                        cacheLifetime = mockBackgroundCacheLifetime
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsListener = mockEntitlementsListener
                    }
            every { mockApplication.isDebuggable } returns false
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, prodEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockLoggerConfig,
                mockNetworkConfig,
                mockBackgroundCacheLifetime,
                mockEntitlementsListener
            )

            // when
            val result = builder.build()

            // then
            assertThat(result).isEqualToComparingFieldByField(expResult)
            verify(exactly = 0) { Log.w(any(), any<String>()) }
        }

        @Test
        fun `building with blank project key`() {
            listOf("", "   ").forEach { projectKey ->
                // given
                val builder =
                    QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)

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
