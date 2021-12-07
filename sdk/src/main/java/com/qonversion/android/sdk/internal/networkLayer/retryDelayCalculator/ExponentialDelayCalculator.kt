package com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

private const val JITTER = 0.4f
private const val FACTOR = 2.4f
// Internal for testing
internal const val MAX_DELAY_MS = 1000000L

internal class ExponentialDelayCalculator(
    private val randomizer: Random
) : RetryDelayCalculator {
    private val jitter = JITTER
    private val factor = FACTOR
    private val maxDelayMS = MAX_DELAY_MS

    @Throws(IllegalArgumentException::class)
    override fun countDelay(minDelay: Long, retriesCount: Int): Long {
        var delay: Long = (minDelay + factor.pow(retriesCount)).toLong()
        var delta: Long = (delay * jitter).roundToLong()

        if (delta != Long.MAX_VALUE) {
            delta += 1L
        }

        delay += randomizer.nextLong(delta)

        // On big attempt indexes may overflow long bounds and become negative.
        return if (delay >= 0) {
            min(delay, maxDelayMS)
        } else {
            maxDelayMS
        }
    }
}
