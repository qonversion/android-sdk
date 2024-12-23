package io.qonversion.nocodes.internal.networkLayer

private const val DEFAULT_RETRY_COUNT = 3
private const val DEFAULT_MIN_DELAY_MS = 500L

internal sealed class RetryPolicy {
    object None : RetryPolicy()

    class Exponential(
        val retryCount: Int = DEFAULT_RETRY_COUNT,
        val minDelay: Long = DEFAULT_MIN_DELAY_MS
    ) : RetryPolicy()

    class InfiniteExponential(
        val minDelay: Long = DEFAULT_MIN_DELAY_MS
    ) : RetryPolicy()
}