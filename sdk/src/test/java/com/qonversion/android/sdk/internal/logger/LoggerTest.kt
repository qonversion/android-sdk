package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.LoggerConfigProvider
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.mockk
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class LoggerTest {

    private lateinit var logger: Logger

    private val mockLoggerConfigProvider = mockk<LoggerConfigProvider>()
    private val mockLoggerConfig = mockk<LoggerConfig>()

    private val capturedTag = slot<String>()
    private val capturedMessage = slot<String>()
    private val capturedThrowable = slot<Throwable>()

    private val logMessage = "test message"
    private val tag = "TestTag"

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)

        every {
            mockLoggerConfigProvider.loggerConfig
        } returns mockLoggerConfig
        every {
            mockLoggerConfig.logTag
        } returns tag

        every {
            Log.v(capture(capturedTag), capture(capturedMessage))
        } returns 0
        every {
            Log.i(capture(capturedTag), capture(capturedMessage))
        } returns 0
        every {
            Log.w(capture(capturedTag), capture(capturedMessage))
        } returns 0
        every {
            Log.e(capture(capturedTag), capture(capturedMessage), capture(capturedThrowable))
        } returns 0
    }

    @AfterEach
    fun after() {
        unmockkStatic(Log::class)
    }

    @Nested
    inner class VerboseTest {
        @BeforeEach
        fun setUp() {
            every {
                mockLoggerConfig.logLevel
            } returns LogLevel.Verbose

            logger = ConsoleLogger(mockLoggerConfigProvider)
        }

        @Test
        fun `should log verbose`() {
            testLogVerboseSuccess()
        }

        @Test
        fun `should log info`() {
            testLogInfoSuccess()
        }

        @Test
        fun `should log warn`() {
            testLogWarnSuccess()
        }

        @Test
        fun `should log error`() {
            testLogErrorSuccess()
        }
    }

    @Nested
    inner class InfoTest {
        @BeforeEach
        fun setUp() {
            every {
                mockLoggerConfig.logLevel
            } returns LogLevel.Info

            logger = ConsoleLogger(mockLoggerConfigProvider)
        }

        @Test
        fun `should not log verbose`() {
            testLogVerboseFailure()
        }

        @Test
        fun `should log info`() {
            testLogInfoSuccess()
        }

        @Test
        fun `should log warn`() {
            testLogWarnSuccess()
        }

        @Test
        fun `should log error`() {
            testLogErrorSuccess()
        }
    }

    @Nested
    inner class WarnTest {
        @BeforeEach
        fun setUp() {
            every {
                mockLoggerConfig.logLevel
            } returns LogLevel.Warning

            logger = ConsoleLogger(mockLoggerConfigProvider)
        }

        @Test
        fun `should not log verbose`() {
            testLogVerboseFailure()
        }

        @Test
        fun `should not log info`() {
            testLogInfoFailure()
        }

        @Test
        fun `should log warn`() {
            testLogWarnSuccess()
        }

        @Test
        fun `should log error`() {
            testLogErrorSuccess()
        }
    }

    @Nested
    inner class ErrorTest {
        @BeforeEach
        fun setUp() {
            every {
                mockLoggerConfig.logLevel
            } returns LogLevel.Error

            logger = ConsoleLogger(mockLoggerConfigProvider)
        }

        @Test
        fun `should not log verbose`() {
            testLogVerboseFailure()
        }

        @Test
        fun `should not log info`() {
            testLogInfoFailure()
        }

        @Test
        fun `should not log warn`() {
            testLogWarnFailure()
        }

        @Test
        fun `should log error`() {
            testLogErrorSuccess()
        }
    }

    @Nested
    inner class DisabledTest {
        @BeforeEach
        fun setUp() {
            every {
                mockLoggerConfig.logLevel
            } returns LogLevel.Disabled

            logger = ConsoleLogger(mockLoggerConfigProvider)
        }

        @Test
        fun `should not log verbose`() {
            testLogVerboseFailure()
        }

        @Test
        fun `should not log info`() {
            testLogInfoFailure()
        }

        @Test
        fun `should not log warn`() {
            testLogWarnFailure()
        }

        @Test
        fun `should not log error`() {
            testLogErrorFailure()
        }
    }

    private fun testLogVerboseSuccess() {
        // given

        // when
        logger.verbose(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
        assertThat(capturedTag.captured).isEqualTo(tag)
    }

    private fun testLogInfoSuccess() {
        // given

        // when
        logger.info(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
        assertThat(capturedTag.captured).isEqualTo(tag)
    }

    private fun testLogWarnSuccess() {
        // given

        // when
        logger.warn(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
        assertThat(capturedTag.captured).isEqualTo(tag)
    }

    private fun testLogErrorSuccess() {
        // given
        val throwable = mockk<Throwable>()

        // when
        logger.error(logMessage, throwable)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
        assertThat(capturedTag.captured).isEqualTo(tag)
        assertThat(capturedThrowable.captured).isSameAs(throwable)
    }

    private fun testLogVerboseFailure() {
        // given

        // when
        logger.verbose(logMessage)

        // then
        verify(exactly = 0) { Log.v(any(), any()) }
    }

    private fun testLogInfoFailure() {
        // given

        // when
        logger.info(logMessage)

        // then
        verify(exactly = 0) { Log.i(any(), any()) }
    }

    private fun testLogWarnFailure() {
        // given

        // when
        logger.warn(logMessage)

        // then
        verify(exactly = 0) {
            Log.w(any(), any<String>())
            Log.w(any(), any<Throwable>())
        }
    }

    private fun testLogErrorFailure() {
        // given

        // when
        logger.error(logMessage)

        // then
        verify(exactly = 0) { Log.e(any(), any(), any()) }
    }
}
