package com.qonversion.android.sdk

import android.app.Application
import android.content.Context
import android.util.Log
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.internal.application
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
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
    private val mockLaunchMode = mockk<QLaunchMode>()
    private val mockEnvironment = mockk<QEnvironment>()
    private val mockEntitlementsListener = mockk<EntitlementsUpdateListener>()
    private val mockEntitlementsCacheLifetime = mockk<QEntitlementsCacheLifetime>()
    private val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, mockEnvironment)
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
                mockCacheConfig,
                mockEntitlementsListener
            )

            // then
            assertThat(config.application).isSameAs(mockApplication)
            assertThat(config.primaryConfig).isSameAs(mockPrimaryConfig)
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
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)

            // when
            builder.setEnvironment(mockEnvironment)

            // then
            assertThat(builder.environment).isSameAs(mockEnvironment)
        }

        @Test
        fun `setting entitlements cache lifetime`() {
            // given
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)
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
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)
                    .apply {
                        environment = mockEnvironment
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                        entitlementsUpdateListener = mockEntitlementsListener
                    }
            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
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
            val defaultEnvironment = QEnvironment.Production
            val defaultEntitlementsCacheLifetime = QEntitlementsCacheLifetime.MONTH

            val mockContext = mockk<Context>(relaxed = true)
            val mockApplication = mockk<Application>()
            every { mockContext.application } returns mockApplication

            val builder = QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)

            val expPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, defaultEnvironment)
            val expCacheConfig = CacheConfig(defaultEntitlementsCacheLifetime)
            val expResult = QonversionConfig(
                mockApplication,
                expPrimaryConfig,
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
            val sandboxEnvironment = QEnvironment.Sandbox
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)
                    .apply {
                        environment = sandboxEnvironment
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns false
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, sandboxEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
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
            val prodEnvironment = QEnvironment.Production
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)
                    .apply {
                        environment = prodEnvironment
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns true
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, prodEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
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
            val sandboxEnvironment = QEnvironment.Sandbox
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)
                    .apply {
                        environment = sandboxEnvironment
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns true
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, sandboxEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
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
            val prodEnvironment = QEnvironment.Production
            val builder =
                QonversionConfig.Builder(mockContext, projectKey, mockLaunchMode)
                    .apply {
                        environment = prodEnvironment
                        entitlementsCacheLifetime = mockEntitlementsCacheLifetime
                    }
            every { mockContext.isDebuggable } returns false
            val mockPrimaryConfig = PrimaryConfig(projectKey, mockLaunchMode, prodEnvironment)

            val expResult = QonversionConfig(
                mockApplication,
                mockPrimaryConfig,
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
                    QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode)

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
