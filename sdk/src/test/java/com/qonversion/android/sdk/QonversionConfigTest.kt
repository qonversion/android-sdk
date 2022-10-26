package com.qonversion.android.sdk

import android.app.Application
import android.content.Context
import android.util.Log
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.dto.Store
import com.qonversion.android.sdk.internal.application
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.dto.config.StoreConfig
import com.qonversion.android.sdk.internal.isDebuggable
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener
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
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalStateException

internal class QonversionConfigTest {

    private val mockContext = mockk<Context>()
    private val mockApplication = mockk<Application>()
    private val projectKey = "some project key"
    private val mockLaunchMode = mockk<LaunchMode>()
    private val mockStore = mockk<Store>()
    private val mockEnvironment = mockk<Environment>()
    private val mockEntitlementsListener = mockk<EntitlementsUpdateListener>()
    private val mockShouldConsumePurchases = true
    private val mockEntitlementsCacheLifetime = mockk<QEntitlementsCacheLifetime>()
    private val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, mockEnvironment)
    private val mockStoreConfig = StoreConfig(mockStore, mockShouldConsumePurchases)
    private val mockCacheConfig = CacheConfig(mockEntitlementsCacheLifetime)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
    }

    @Nested
    inner class GettersTest {

        @Test
        fun getters() {
            // given
            val config = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockCacheConfig,
                mockEntitlementsListener
            )

            // then
            assertThat(config.application).isSameAs(mockApplication)
            assertThat(config.primaryConfig).isSameAs(mockPrimaryConfig)
            assertThat(config.storeConfig).isSameAs(mockStoreConfig)
            assertThat(config.cacheConfig).isSameAs(mockCacheConfig)
            assertThat(config.entitlementsUpdateListener).isSameAs(mockEntitlementsListener)
        }
    }

    @Nested
    inner class SettersTest {

        @Test
        fun `setting environment type`() {
            // given
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)

            // when
            builder.setEnvironment(mockEnvironment)

            // then
            assertThat(builder.environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `setting should consume purchases`() {
            // given
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
            builder.shouldConsumePurchases = true

            // when
            builder.setShouldConsumePurchases(false)

            // then
            assertThat(builder.shouldConsumePurchases).isEqualTo(false)
        }

        @Test
        fun `setting entitlements cache lifetime`() {
            // given
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
            builder.entitlementsCacheLifetime = QEntitlementsCacheLifetime.MONTH

            // when
            builder.setEntitlementsCacheLifetime(QEntitlementsCacheLifetime.WEEK)

            // then
            assertThat(builder.entitlementsCacheLifetime).isEqualTo(QEntitlementsCacheLifetime.WEEK)
        }
    }

    @Nested
    inner class BuildMethodTest {

        @BeforeEach
        fun setUp() {
            mockkStatic("com.qonversion.android.sdk.internal.ExtensionsKt")
            every { mockContext.application } returns mockApplication
        }

        @Test
        fun `successful build with full list of arguments`() {
            // given
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = mockEnvironment
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                        entitlementsUpdateListener = mockEntitlementsListener
                    }
            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockCacheConfig,
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
            val defaultShouldConsumePurchases = true
            val defaultEnvironment = Environment.Production
            val defaultEntitlementsCacheLifetime = QEntitlementsCacheLifetime.MONTH

            val mockContext = mockk<Context>(relaxed = true)
            val mockApplication = mockk<Application>()
            every { mockContext.application } returns mockApplication

            val builder = QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)

            val expPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, defaultEnvironment)
            val expStoreConfig = StoreConfig(defaultStore, defaultShouldConsumePurchases)
            val expCacheConfig = CacheConfig(defaultEntitlementsCacheLifetime)
            val expResult = QonversionConfig(
                mockApplication,
                expPrimaryConfig,
                expStoreConfig,
                expCacheConfig,
                null
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
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = sandboxEnvironment
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns false
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, sandboxEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockCacheConfig,
                null
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
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = prodEnvironment
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns true
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, prodEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockCacheConfig,
                null
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
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = sandboxEnvironment
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns true
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, sandboxEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockCacheConfig,
                null
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
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode, mockStore)
                    .apply {
                        environment = prodEnvironment
                        shouldConsumePurchases = mockShouldConsumePurchases
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns false
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, prodEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
                mockStoreConfig,
                mockCacheConfig,
                null
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
                assertThrows<IllegalStateException> { builder.build() }
            }
        }

        @AfterEach
        fun after() {
            unmockkStatic("com.qonversion.android.sdk.internal.ExtensionsKt")
        }
    }

    @AfterEach
    fun after() {
        unmockkStatic(Log::class)
    }
}
