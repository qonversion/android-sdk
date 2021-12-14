package com.qonversion.android.sdk.internal.logger

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

internal class LoggerTest {

    private lateinit var releaseLogger: Logger
    private lateinit var debugLogger: Logger
    private val logTag = "custom tag"
    private val logMessage = "test message"
    private val capturedTag = slot<String>()
    private val capturedMessage = slot<String>()

    @Before
    fun setUp() {
        releaseLogger = ConsoleLogger(false)
        debugLogger = ConsoleLogger(true)

        mockkStatic(Log::class)
        every {
            Log.println(Log.DEBUG, capture(capturedTag), capture(capturedMessage))
        } returns 0
    }

    @Test
    fun `test simple release log`() {
        // given

        // when
        releaseLogger.release(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
    }

    @Test
    fun `test simple release log while debugging`() {
        // given

        // when
        debugLogger.release(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
    }

    @Test
    fun `test simple debug log`() {
        // given

        // when
        releaseLogger.debug(logMessage)

        // then
        verify(exactly = 0) { Log.println(Log.DEBUG, any(), any()) }
    }

    @Test
    fun `test simple debug log while debugging`() {
        // given

        // when
        debugLogger.debug(logMessage)

        // then
        assertThat(capturedMessage.captured).contains(logMessage)
    }

    @Test
    fun `test simple debug log with tag`() {
        // given

        // when
        releaseLogger.debug(logTag, logMessage)

        // then
        verify(exactly = 0) { Log.println(Log.DEBUG, any(), any()) }
    }

    @Test
    fun `test simple debug log with tag while debugging`() {
        // given

        // when
        debugLogger.debug(logTag, logMessage)

        // then
        assertThat(capturedTag.captured).isEqualTo(logTag)
        assertThat(capturedMessage.captured).contains(logMessage)
    }
}