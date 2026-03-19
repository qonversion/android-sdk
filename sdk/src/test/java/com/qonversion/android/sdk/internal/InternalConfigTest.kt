package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.listeners.QDeferredPurchasesListener
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// Review feedback (Task 6): updated tests to reflect removal of entitlementsUpdateListener
// from InternalConfig. Only deferredPurchasesListener remains.
internal class InternalConfigTest {
    private lateinit var internalConfig: InternalConfig

    private val mockPrimaryConfig = mockk<PrimaryConfig>()
    private val mockCacheConfig = mockk<CacheConfig>()
    private val mockDeferredPurchasesListener = mockk<QDeferredPurchasesListener>()

    @BeforeEach
    fun setUp() {
        internalConfig = InternalConfig(
            mockPrimaryConfig,
            mockCacheConfig,
            mockDeferredPurchasesListener
        )
    }

    @Nested
    inner class ConstructorsTest {
        @Test
        fun `primary constructor without optional params`() {
            // given

            // when
            val internalConfig = InternalConfig(
                mockPrimaryConfig,
                mockCacheConfig
            )

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.cacheConfig).isSameAs(mockCacheConfig)
            assertThat(internalConfig.deferredPurchasesListener).isNull()
        }

        @Test
        fun `primary constructor with deferred purchases listener`() {
            // given

            // when
            val internalConfig = InternalConfig(
                mockPrimaryConfig,
                mockCacheConfig,
                mockDeferredPurchasesListener
            )

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.cacheConfig).isSameAs(mockCacheConfig)
            assertThat(internalConfig.deferredPurchasesListener).isSameAs(mockDeferredPurchasesListener)
        }

        @Test
        fun `constructor with qonversion config`() {
            // given
            val qonversionConfig = QonversionConfig(
                mockk(),
                mockPrimaryConfig,
                mockCacheConfig,
                mockDeferredPurchasesListener
            )

            // when
            val internalConfig = InternalConfig(qonversionConfig)

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.cacheConfig).isSameAs(mockCacheConfig)
            assertThat(internalConfig.deferredPurchasesListener).isSameAs(mockDeferredPurchasesListener)
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
    }

    @Nested
    inner class EnvironmentProviderTest {
        private val mockProjectKey = "projectKey"
        private val mockLaunchMode = mockk<QLaunchMode>()
        private val mockEnvironment = mockk<QEnvironment>()

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
            val environment = QEnvironment.Sandbox
            internalConfig.primaryConfig = PrimaryConfig(mockProjectKey, mockLaunchMode, environment)

            // when
            val isSandbox = environmentProvider.isSandbox

            // then
            assertThat(isSandbox).isTrue()
        }

        @Test
        fun `is not sandbox when prod env`() {
            // given
            val environmentProvider: EnvironmentProvider = internalConfig
            val environment = QEnvironment.Production
            internalConfig.primaryConfig = PrimaryConfig(mockProjectKey, mockLaunchMode, environment)

            // when
            val isSandbox = environmentProvider.isSandbox

            // then
            assertThat(isSandbox).isFalse()
        }
    }
}
