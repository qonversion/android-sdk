package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class RetryConfig(
    internal val shouldRetry: Boolean,
    internal val attemptIndex: Int = 0,
    internal val delay: Long = 0L
)

class NetworkClientDecoratorImpl internal constructor(
    private val networkClient: NetworkClient,
    private val defaultRetryPolicy: RetryPolicy = RetryPolicy.Exponential(),
    private val delayCalculator: RetryDelayCalculator,
    private val config: InternalConfig
) : NetworkClientDecorator {
    override suspend fun execute(request: Request): Response {
        return execute(request, defaultRetryPolicy)
    }

    override suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response {
        return execute(request, retryPolicy, 0)
    }

    private suspend fun execute(request: Request, retryPolicy: RetryPolicy, attemptIndex: Int): Response {
        return withContext(Dispatchers.IO) {
            if (config.requestsShouldBeDenied) {
                throw QonversionException(ErrorCode.RequestDenied)
            }
            val response = networkClient.execute(request)
            if (response.isSuccess) {
                response
            } else {
                val retryConfig: RetryConfig = prepareRetryConfig(retryPolicy, attemptIndex)
                if (retryConfig.shouldRetry) {
                    delay(retryConfig.delay)
                    execute(request, retryPolicy, retryConfig.attemptIndex)
                } else {
                    response
                }
            }
        }
    }

    private fun prepareRetryConfig(retryPolicy: RetryPolicy, attemptIndex: Int): RetryConfig {
        var shouldRetry = false
        val newAttemptIndex = attemptIndex + 1
        var minDelay = 0L
        var delay = 0L

        if (retryPolicy is RetryPolicy.InfiniteExponential) {
            shouldRetry = true
            minDelay = retryPolicy.minDelay
        } else if (retryPolicy is RetryPolicy.Exponential) {
            shouldRetry = retryPolicy.retryCount > attemptIndex
            minDelay = retryPolicy.minDelay
        }

        if (minDelay < 0) {
            shouldRetry = false
        }

        if (shouldRetry) {
            delay = delayCalculator.countDelay(minDelay, newAttemptIndex)
        }

        return RetryConfig(shouldRetry, newAttemptIndex, delay)
    }
}
