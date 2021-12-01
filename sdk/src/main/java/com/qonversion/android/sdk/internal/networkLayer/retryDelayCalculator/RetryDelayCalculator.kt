package com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator

internal interface RetryDelayCalculator {
    @Throws(IllegalArgumentException::class)
    fun countDelay(minDelay: Long, retriesCount: Int): Long
}
