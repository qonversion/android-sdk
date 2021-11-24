package com.qonversion.android.sdk.internal.networkLayer

private const val DEFAULT_RETRY_COUNT = 3
private const val DEFAULT_MIN_DELAY_MS = 500L

sealed class RetryPolicy {
    object None: RetryPolicy()

    class Exponential(
        val retryCount: Int = DEFAULT_RETRY_COUNT,
        val minDelay: Long = DEFAULT_MIN_DELAY_MS
    ): RetryPolicy()

    class InfinityExponential(
        val minDelay: Long = DEFAULT_MIN_DELAY_MS
    ): RetryPolicy()
}