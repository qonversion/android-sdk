package com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

class ExponentialDelayCalculator(
    private val randomizer: Random
    ): RetryDelayCalculator {
    private val jitter = 0.4f
    private val factor = 2.4f
    private val maxDelayMS = 1000000L

    @Throws(IllegalArgumentException::class)
    override fun countDelay(minDelay: Long, retriesCount: Int): Long {
        var delay: Long = (minDelay + factor.pow(retriesCount)).toLong()
        var delta: Long = (delay * jitter).roundToLong()

        if (delta != Long.MAX_VALUE) {
            delta += 1L
        }

        delay += randomizer.nextLong(delta)
        val resultDelay = min(delay, maxDelayMS)

        return resultDelay
    }
}