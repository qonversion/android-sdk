package com.qonversion.android.sdk

import java.util.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class IncrementalCounter(private val randomizer: Random) {

    companion object {
        private const val JITTER = 0.4f
        private const val FACTOR = 2.4f
        private const val MAX_DELAY = 1000
    }

    fun countDelay(minDelay: Int, retriesCount: Int): Int {
        var delay = minDelay + FACTOR.pow(retriesCount)
        val delta = (delay * JITTER).roundToInt()
        delay += randomizer.nextInt((delta + 1))
        val resultDelay = min(delay.roundToInt(), MAX_DELAY)

        return resultDelay
    }
}