package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.dto.config.StoreConfig
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InternalConfigTest {
    private lateinit var internalConfig: InternalConfig

    private val mockPrimaryConfig = mockk<PrimaryConfig>()
    private val mockStoreConfig = mockk<StoreConfig>()

    @BeforeEach
    fun setUp() {
        internalConfig = InternalConfig(
            mockPrimaryConfig,
            mockStoreConfig
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
                mockStoreConfig
            )

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.storeConfig).isSameAs(mockStoreConfig)
        }

        @Test
        fun `constructor with qonversion config`() {
            // given
            val qonversionConfig = QonversionConfig(
                mockk(),
                mockPrimaryConfig,
                mockStoreConfig
            )

            // when
            val internalConfig = InternalConfig(qonversionConfig)

            // then
            assertThat(internalConfig.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(internalConfig.storeConfig).isSameAs(mockStoreConfig)
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
            assertThat(isSandbox).isTrue()
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
            assertThat(isSandbox).isFalse()
        }
    }
}