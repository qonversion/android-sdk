package com.qonversion.android.sdk.internal.logger

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class LoggerTest {

    private lateinit var logger: Logger

    private val mockLoggerConfig = mockk<LoggerConfig>()

    private val capturedTag = slot<String>()
    private val capturedMessage = slot<String>()

    private val logMessage = "test message"
    private val tag = "TestTag"

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)

        every {
            mockLoggerConfig.logTag
        } returns tag
    }

    @Nested
    inner class VerboseTest {
        @BeforeEach
        fun setUp() {
            every {
                mockLoggerConfig.logLevel
            } returns LogLevel.Verbose

            logger = ConsoleLogger(mockLoggerConfig)

            every {
                Log.println(Log.VERBOSE, capture(capturedTag), capture(capturedMessage))
            } returns 0
            every {
                Log.println(Log.INFO, capture(capturedTag), capture(capturedMessage))
            } returns 0
            every {
                Log.println(Log.WARN, capture(capturedTag), capture(capturedMessage))
            } returns 0
            every {
                Log.println(Log.ERROR, capture(capturedTag), capture(capturedMessage))
            } returns 0
        }

        @Test
        fun `should log verbose`() {
            // given

            // when
            logger.verbose(logMessage)

            // then
            assertThat(capturedMessage.captured).contains(logMessage)
            assertThat(capturedTag.captured).isEqualTo(tag)
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

            logger = ConsoleLogger(mockLoggerConfig)

            every {
                Log.println(Log.INFO, capture(capturedTag), capture(capturedMessage))
            } returns 0
            every {
                Log.println(Log.WARN, capture(capturedTag), capture(capturedMessage))
            } returns 0
            every {
                Log.println(Log.ERROR, capture(capturedTag), capture(capturedMessage))
            } returns 0
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

            logger = ConsoleLogger(mockLoggerConfig)
            
            every {
                Log.println(Log.WARN, capture(capturedTag), capture(capturedMessage))
            } returns 0
            every {
                Log.println(Log.ERROR, capture(capturedTag), capture(capturedMessage))
            } returns 0
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

            logger = ConsoleLogger(mockLoggerConfig)
            
            every {
                Log.println(Log.ERROR, capture(capturedTag), capture(capturedMessage))
            } returns 0
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

            logger = ConsoleLogger(mockLoggerConfig)
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

        // when
        logger.error(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
        assertThat(capturedTag.captured).isEqualTo(tag)
    }

    private fun testLogVerboseFailure() {
        // given

        // when
        logger.verbose(logMessage)

        // then
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }

    private fun testLogInfoFailure() {
        // given

        // when
        logger.info(logMessage)

        // then
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }

    private fun testLogWarnFailure() {
        // given

        // when
        logger.warn(logMessage)

        // then
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }

    private fun testLogErrorFailure() {
        // given

        // when
        logger.error(logMessage)

        // then
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }
}
