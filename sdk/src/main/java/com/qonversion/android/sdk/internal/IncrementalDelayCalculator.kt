package com.qonversion.android.sdk.internal

import java.util.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal class IncrementalDelayCalculator(private val randomizer: Random) {

    companion object {
        private const val JITTER = 0.4f
        private const val FACTOR = 2.4f
        private const val MAX_DELAY = 1000
    }

    @Throws(IllegalArgumentException::class)
    fun countDelay(minDelay: Int, retriesCount: Int): Int {
        var delay = minDelay + FACTOR.pow(retriesCount)
        var delta = (delay * JITTER).roundToInt()

        if (delta != Int.MAX_VALUE) {
            delta += 1
        }

        delay += randomizer.nextInt(delta)
        val resultDelay = min(delay.roundToInt(), MAX_DELAY)

        return resultDelay
    }
}
