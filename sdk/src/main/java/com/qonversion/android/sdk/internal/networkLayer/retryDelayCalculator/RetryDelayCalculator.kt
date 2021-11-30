package com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator

interface RetryDelayCalculator {
    @Throws(IllegalArgumentException::class)
    fun countDelay(minDelay: Long, retriesCount: Int): Long
}
