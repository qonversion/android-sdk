package com.qonversion.android.sdk.old

import com.qonversion.android.sdk.old.IncrementalDelayCalculator
import io.mockk.*
import org.junit.Assert.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class IncrementalDelayCalculatorTest {
    private val mockRandomizer = mockk<Random>(relaxed = true)
    private val random = 3
    private val minDelay = 5

    private lateinit var delayCalculator: IncrementalDelayCalculator

    @BeforeEach
    fun setUp() {
        delayCalculator = IncrementalDelayCalculator(mockRandomizer)
    }

    @Nested
    inner class CountDelay {
        @Test
        fun `should increment delay when counter = 0`() {
            // given
            every {
                mockRandomizer.nextInt(any())
            } returns random

            // when
            val result = delayCalculator.countDelay(minDelay, 0)

            // then
            assertEquals(9, result)
        }

        @Test
        fun `should increment delay when counter = 1`() {
            // given
            every {
                mockRandomizer.nextInt(any())
            } returns random

            // when
            val result = delayCalculator.countDelay(minDelay, 1)

            // then
            assertEquals(10, result)
        }

        @Test
        fun `should increment delay when counter = 10`() {
            // given
            every {
                mockRandomizer.nextInt(any())
            } returns random

            // when
            val result = delayCalculator.countDelay(minDelay, 10)

            // then
            assertEquals(1000, result)
        }
    }
}