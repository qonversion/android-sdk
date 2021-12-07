package com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.math.max
import kotlin.random.Random

internal class ExponentialDelayCalculatorTest {

    private lateinit var delayCalculator: RetryDelayCalculator
    private val random = mockk<Random>()

    @Before
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
    fun `first retry`() {
        // given

        // when
        val delay = delayCalculator.countDelay(0, 0)

        // then
        assertThat(delay > 0)
    }

    @Test
    fun `min delay`() {
        // given
        val minDelay = 1000L

        // when
        val delay = delayCalculator.countDelay(minDelay, 0)

        // then
        assertThat(delay >= minDelay)
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
        assertThat(delay1 >= minDelay)
        assertThat(delay2 > delay1)
        assertThat(delay3 > (delay2 - delay1))
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