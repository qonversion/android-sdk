package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.provider.NetworkConfigHolder
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.provider.EntitlementsUpdateListenerProvider
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.LoggerConfigProvider
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InternalConfigTest {
    private lateinit var internalConfig: InternalConfig

    private val mockPrimaryConfig = mockk<PrimaryConfig>()
    private val mockStoreConfig = mockk<StoreConfig>()
    private val mockNetworkConfig = mockk<NetworkConfig>()
    private val mockLoggerConfig = mockk<LoggerConfig>()
    private val mockCacheLifetimeConfig = mockk<CacheLifetimeConfig>()
    private val mockEntitlementsUpdateListener = mockk<EntitlementsUpdateListener>()
    private val mockBackgroundCacheLifetime = mockk<CacheLifetime>()
    private val mockBackgroundInternalCacheLifetime = mockk<InternalCacheLifetime>()

    @BeforeEach
    fun setUp() {
        mockkObject(InternalCacheLifetime)
        every {
            InternalCacheLifetime.from(mockBackgroundCacheLifetime)
        } returns mockBackgroundInternalCacheLifetime

        internalConfig = InternalConfig(
            mockPrimaryConfig,
            mockStoreConfig,
            mockNetworkConfig,
            mockLoggerConfig,
            mockCacheLifetimeConfig,
            mockEntitlementsUpdateListener
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(InternalCacheLifetime)
    }

    @Nested
    inner class ConstructorsTest {
        @Test
        fun `primary constructor without optional params`() {
            // given

            // when
            val internalConfig = InternalConfig(
                mockPrimaryConfig,
                mockStoreConfig,
                mockNetworkConfig,
                mockLoggerConfig,
                mockCacheLifetimeConfig
            )

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.storeConfig).isSameAs(mockStoreConfig)
            assertThat(internalConfig.networkConfig).isSameAs(mockNetworkConfig)
            assertThat(internalConfig.loggerConfig).isSameAs(mockLoggerConfig)
            assertThat(internalConfig.cacheLifetimeConfig).isSameAs(mockCacheLifetimeConfig)
            assertThat(internalConfig.entitlementsUpdateListener).isNull()
        }

        @Test
        fun `constructor with qonversion config`() {
            // given
            val expectedCacheLifetimeConfig = CacheLifetimeConfig(
                mockBackgroundInternalCacheLifetime,
                InternalCacheLifetime.FiveMin
            )
            val qonversionConfig = QonversionConfig(
                mockk(),
                mockPrimaryConfig,
                mockStoreConfig,
                mockLoggerConfig,
                mockNetworkConfig,
                mockBackgroundCacheLifetime,
                mockEntitlementsUpdateListener
            )

            // when
            val internalConfig = InternalConfig(qonversionConfig)

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.storeConfig).isSameAs(mockStoreConfig)
            assertThat(internalConfig.networkConfig).isSameAs(mockNetworkConfig)
            assertThat(internalConfig.loggerConfig).isSameAs(mockLoggerConfig)
            assertThat(internalConfig.cacheLifetimeConfig).isEqualTo(expectedCacheLifetimeConfig)
            assertThat(internalConfig.entitlementsUpdateListener).isSameAs(mockEntitlementsUpdateListener)

            verify { InternalCacheLifetime.from(mockBackgroundCacheLifetime) }
        }
    }

    @Nested
    inner class SettersTest {

        @Test
        fun `set uid`() {
            // given
            val uid = "test_uid"

            // when
            internalConfig.uid = uid

            // then
            assertThat(internalConfig.uid).isEqualTo(uid)
        }

        @Test
        fun `set entitlements update listener`() {
            // given
            val listener = mockk<EntitlementsUpdateListener>()

            // when
            internalConfig.entitlementsUpdateListener = listener

            // then
            assertThat(internalConfig.entitlementsUpdateListener).isSameAs(listener)
        }
    }

    @Nested
    inner class EnvironmentProviderTest {
        private val mockProjectKey = "projectKey"
        private val mockLaunchMode = mockk<LaunchMode>()
        private val mockEnvironment = mockk<Environment>()

        @Test
        fun `get environment`() {
            // given
            val environmentProvider: EnvironmentProvider = internalConfig
            val primaryConfig = PrimaryConfig(mockProjectKey, mockLaunchMode, mockEnvironment)
            internalConfig.primaryConfig = primaryConfig

            // when
            val environment = environmentProvider.environment

            // then
            assertThat(environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `is sandbox when sandbox env`() {
            // given
            val environmentProvider: EnvironmentProvider = internalConfig
            val environment = Environment.Sandbox
            internalConfig.primaryConfig = PrimaryConfig(mockProjectKey, mockLaunchMode, environment)

            // when
            val isSandbox = environmentProvider.isSandbox

            // then
            assertThat(isSandbox).isTrue
        }

        @Test
        fun `is not sandbox when prod env`() {
            // given
            val environmentProvider: EnvironmentProvider = internalConfig
            val environment = Environment.Production
            internalConfig.primaryConfig = PrimaryConfig(mockProjectKey, mockLaunchMode, environment)

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
            val loggerConfigProvider: LoggerConfigProvider = internalConfig
            internalConfig.loggerConfig = mockLoggerConfig

            // when
            val logLevel = loggerConfigProvider.logLevel

            // then
            assertThat(logLevel).isSameAs(mockLogLevel)
        }

        @Test
        fun `get log tag`() {
            // given
            val loggerConfigProvider: LoggerConfigProvider = internalConfig
            internalConfig.loggerConfig = mockLoggerConfig

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
            val cacheLifetimeConfigProvider: CacheLifetimeConfigProvider = internalConfig
            internalConfig.cacheLifetimeConfig = mockCacheLifetimeLoggerConfig

            // when
            val cacheLifetimeConfig = cacheLifetimeConfigProvider.cacheLifetimeConfig

            // then
            assertThat(cacheLifetimeConfig).isSameAs(mockCacheLifetimeLoggerConfig)
        }
    }

    @Nested
    inner class NetworkConfigHolderTest {
        private val mockCanSendRequests = true

        @BeforeEach
        fun setUp() {
            val networkConfig = NetworkConfig()

            internalConfig = InternalConfig(
                mockPrimaryConfig,
                mockStoreConfig,
                networkConfig,
                mockLoggerConfig,
                mockCacheLifetimeConfig,
                mockEntitlementsUpdateListener
            )
        }

        @Test
        fun `get canSendRequests`() {
            // given
            internalConfig.networkConfig.canSendRequests = mockCanSendRequests
            val networkConfigHolder: NetworkConfigHolder = internalConfig

            // when
            val canSendRequests = networkConfigHolder.canSendRequests

            // then
            assertThat(canSendRequests).isSameAs(mockCanSendRequests)
        }

        @Test
        fun `set canSendRequests`() {
            // given
            val networkConfigHolder: NetworkConfigHolder = internalConfig

            // when
            networkConfigHolder.canSendRequests = mockCanSendRequests

            // then
            assertThat(internalConfig.networkConfig.canSendRequests).isSameAs(mockCanSendRequests)
        }
    }

    @Nested
    inner class EntitlementsUpdateListenerProviderTest {

        @Test
        fun `get entitlement updates listener`() {
            // given
            val mockEntitlementsUpdateListener = mockk<EntitlementsUpdateListener>()
            internalConfig.entitlementsUpdateListener = mockEntitlementsUpdateListener
            val provider: EntitlementsUpdateListenerProvider = internalConfig

            // when
            val result = provider.entitlementsUpdateListener

            // then
            assertThat(result).isSameAs(mockEntitlementsUpdateListener)
        }
    }
}