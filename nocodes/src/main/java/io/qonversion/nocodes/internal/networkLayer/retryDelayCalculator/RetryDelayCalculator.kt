package io.qonversion.nocodes.internal.networkLayer.retryDelayCalculator

internal interface RetryDelayCalculator {
    @Throws(IllegalArgumentException::class)
    fun countDelay(minDelay: Long, retriesCount: Int): Long
}