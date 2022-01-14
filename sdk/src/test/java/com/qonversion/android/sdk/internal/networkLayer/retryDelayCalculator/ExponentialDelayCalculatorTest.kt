package com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.math.max
import kotlin.random.Random

internal class ExponentialDelayCalculatorTest {

    private lateinit var delayCalculator: RetryDelayCalculator
    private val random = mockk<Random>()

    private val commonTestCases = mapOf(
        0L to 0,
        1000L to 0
    )

    @BeforeEach
    fun setUp() {
        delayCalculator = ExponentialDelayCalculator(random)

        val maxValue = slot<Long>()
        every {
            random.nextLong(capture(maxValue))
        } answers {
            max(0, maxValue.captured)
        }
    }

    @Test
    fun `common cases`() {
        for ((minDelay, retriesCount) in commonTestCases) {
            // when
            val delay = delayCalculator.countDelay(minDelay, retriesCount)

            // then
            assertThat(delay).isGreaterThan(minDelay)
        }
    }

    @Test
    fun `every next attempt is delayed more then previous ones`() {
        // given
        val minDelay = 1L

        // when
        val delay1 = delayCalculator.countDelay(minDelay, 0)
        val delay2 = delayCalculator.countDelay(minDelay, 1)
        val delay3 = delayCalculator.countDelay(minDelay, 2)

        // then
        assertThat(delay1).isGreaterThanOrEqualTo(minDelay)
        assertThat(delay2).isGreaterThan(delay1)
        assertThat(delay3).isGreaterThan(delay2 - delay1)
    }

    @Test
    fun `huge attempt index`() {
        // given

        // when
        val res = assertDoesNotThrow {
            delayCalculator.countDelay(0, 100000)
        }

        // then
        assertThat(res).isEqualTo(MAX_DELAY_MS)
    }
}
