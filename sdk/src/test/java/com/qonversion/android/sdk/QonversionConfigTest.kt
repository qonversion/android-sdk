package com.qonversion.android.sdk

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
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

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
    }

    @Nested
    inner class BuilderMethodsTest {

        @Test
        fun `setting environment type`() {
            // given
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore)
            val mockEnvironment = mockk<Environment>()

            // when
            builder.setEnvironment(mockEnvironment)

            // then
            assertThat(builder.environment).isSameAs(mockEnvironment)
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
            val mockEnvironment = mockk<Environment>()
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore).apply {
                environment = mockEnvironment
            }
            val expResult = QonversionConfig(mockApplication, projectKey, mockLaunchMode, mockStore, mockEnvironment)

            // when
            val result = builder.build()

            // then
            verify(exactly = 0) { Log.w(any(), any<String>()) }
            assertThat(result).isEqualTo(expResult)
        }

        @Test
        fun `building sandbox config for release`() {
            // given
            val sandboxEnvironment = Environment.Sandbox
            val builder = QonversionConfig.Builder(mockApplication, projectKey, mockLaunchMode, mockStore).apply {
                environment = sandboxEnvironment
            }
            every { mockApplication.isDebuggable } returns false
            val expResult = QonversionConfig(mockApplication, projectKey, mockLaunchMode, mockStore, sandboxEnvironment)
            val slotWarningMessage = slot<String>()
            every { Log.w(any(), capture(slotWarningMessage)) } returns 0

            // when
            val result = builder.build()

            // then
            assertThat(result).isEqualTo(expResult)
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
            }
            every { mockApplication.isDebuggable } returns true
            val expResult = QonversionConfig(mockApplication, projectKey, mockLaunchMode, mockStore, prodEnvironment)
            val slotWarningMessage = slot<String>()
            every { Log.w(any(), capture(slotWarningMessage)) } returns 0

            // when
            val result = builder.build()

            // then
            assertThat(result).isEqualTo(expResult)
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
