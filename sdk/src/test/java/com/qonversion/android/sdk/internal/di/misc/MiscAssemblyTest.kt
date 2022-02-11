package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import io.mockk.mockk
import io.mockk.every
import io.mockk.slot
import io.mockk.just
import io.mockk.Runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.random.Random

internal class MiscAssemblyTest {
    private lateinit var miscAssembly: MiscAssembly
    private val mockApplication = mockk<Application>()
    private val mockInternalConfig = mockk<InternalConfig>()

    @BeforeEach
    fun setup() {
        miscAssembly = MiscAssemblyImpl(mockApplication, mockInternalConfig)
    }

    @Nested
    inner class LoggerTest {
        @Test
        fun `get logger`() {
            // given
            val expectedResult = ConsoleLogger(mockInternalConfig)

            // when
            val result = miscAssembly.logger()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different loggers`() {
            // given

            // when
            val firstResult = miscAssembly.logger()
            val secondResult = miscAssembly.logger()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get locale`() {
        // given
        val storedLocale = Locale.getDefault()
        val mockLocale = mockk<Locale>()
        Locale.setDefault(mockLocale)  // mock Locale

        // when
        val result = miscAssembly.locale()

        // then
        assertThat(result).isEqualTo(mockLocale)
        Locale.setDefault(storedLocale) // unmock Locale
    }

    @Nested
    inner class JsonSerializerTest {
        @Test
        fun `get serializer`() {
            // given

            // when
            val result = miscAssembly.jsonSerializer()

            // then
            assertThat(result).isInstanceOf(JsonSerializer::class.java)
        }

        @Test
        fun `get different serializers`() {
            // given

            // when
            val firstResult = miscAssembly.jsonSerializer()
            val secondResult = miscAssembly.jsonSerializer()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class ExponentialDelayCalculatorTest {
        @Test
        fun `get delay calculator`() {
            // given
            val expectedResult = ExponentialDelayCalculator(Random)

            // when
            val result = miscAssembly.exponentialDelayCalculator()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
            assertThat(result).isInstanceOf(ExponentialDelayCalculator::class.java)
        }

        @Test
        fun `get different delay calculators`() {
            // given

            // when
            val firstResult = miscAssembly.exponentialDelayCalculator()
            val secondResult = miscAssembly.exponentialDelayCalculator()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class AppLifecycleObserverTest {
        private val lifecycleObserverSlot = slot<AppLifecycleObserverImpl>()

        @BeforeEach
        fun setUp() {
            every {
                mockApplication.registerActivityLifecycleCallbacks(capture(lifecycleObserverSlot))
            } just Runs
        }

        @Test
        fun `get app lifecycle observer`() {
            // given

            // when
            val result = miscAssembly.appLifecycleObserver()

            // then
            assertThat(result).isEqualTo(lifecycleObserverSlot.captured)
        }

        @Test
        fun `get different app lifecycle observers`() {
            // given and when
            val firstResult = miscAssembly.appLifecycleObserver()
            val secondResult = miscAssembly.appLifecycleObserver()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get different delayed workers`() {
        // given

        // when
        val firstResult = miscAssembly.delayedWorker()
        val secondResult = miscAssembly.delayedWorker()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }
}
