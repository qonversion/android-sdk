package com.qonversion.android.sdk.internal.di.misc

import androidx.lifecycle.ProcessLifecycleOwner
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import com.qonversion.android.sdk.internal.user.generator.UserIdGeneratorImpl
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorkerImpl
import io.mockk.mockk
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.runs
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.random.Random
import org.junit.jupiter.api.AfterEach

internal class MiscAssemblyTest {
    private lateinit var miscAssembly: MiscAssembly
    private val mockInternalConfig = mockk<InternalConfig>()

    @BeforeEach
    fun setup() {
        miscAssembly = MiscAssemblyImpl(mockInternalConfig)
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
            assertThat(result).isInstanceOf(ConsoleLogger::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different loggers`() {
            // given

            // when
            val firstResult = miscAssembly.logger()
            val secondResult = miscAssembly.logger()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
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
            assertThat(firstResult).isNotSameAs(secondResult)
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
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class AppLifecycleObserverTest {
        private val slotLifecycleObserver = slot<AppLifecycleObserverImpl>()

        @BeforeEach
        fun setUp() {
            mockkStatic(ProcessLifecycleOwner::class)
            every {
                ProcessLifecycleOwner.get().lifecycle.addObserver(capture(slotLifecycleObserver))
            } just runs
        }

        @Test
        fun `get app lifecycle observer`() {
            // given

            // when
            val result = miscAssembly.appLifecycleObserver()

            // then
            assertThat(result).isInstanceOf(AppLifecycleObserverImpl::class.java)
            assertThat(result).isEqualTo(slotLifecycleObserver.captured)
        }

        @Test
        fun `get different app lifecycle observers`() {
            // given and when
            val firstResult = miscAssembly.appLifecycleObserver()
            val secondResult = miscAssembly.appLifecycleObserver()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }

        @AfterEach
        fun tearDown() {
            unmockkStatic(ProcessLifecycleOwner::class)
        }
    }

    @Nested
    inner class DelayedWorkerTest {
        @Test
        fun `get delayed worker`() {
            // given

            // when
            val result = miscAssembly.delayedWorker()

            // then
            assertThat(result).isInstanceOf(DelayedWorkerImpl::class.java)
        }

        @Test
        fun `get different delayed workers`() {
            // given

            // when
            val firstResult = miscAssembly.delayedWorker()
            val secondResult = miscAssembly.delayedWorker()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserIdGeneratorTest {
        @Test
        fun `get user id generator`() {
            // given

            // when
            val result = miscAssembly.userIdGenerator()

            // then
            assertThat(result).isInstanceOf(UserIdGeneratorImpl::class.java)
        }

        @Test
        fun `get different user id generators`() {
            // given

            // when
            val firstResult = miscAssembly.userIdGenerator()
            val secondResult = miscAssembly.userIdGenerator()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }
}
