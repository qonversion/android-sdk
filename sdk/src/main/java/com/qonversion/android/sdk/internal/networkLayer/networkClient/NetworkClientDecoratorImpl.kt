package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.mappers.Mapper
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import kotlinx.coroutines.delay

data class RetryConfig(
    internal val shouldRetry: Boolean,
    internal val attemptIndex: Int = 0,
    internal val delay: Long = 0L)

class NetworkClientDecoratorImpl internal constructor(
    private val networkClient: NetworkClient,
    private val defaultRetryPolicy: RetryPolicy = RetryPolicy.Exponential(),
    private val delayCalculator: RetryDelayCalculator,
    private val config: InternalConfig
): NetworkClientDecorator {
    override suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response {
        return execute(request, retryPolicy, 0)
    }

    override suspend fun execute(request: Request): Response {
        return execute(request, defaultRetryPolicy)
    }

    private suspend fun execute(request: Request, retryPolicy: RetryPolicy, attemptIndex: Int): Response {
        if (config.requestsShouldBeDenied) {
            // return default error
            return Response(200, mapOf<String, Any>())
        }
        val response = networkClient.execute(request)
        return if (response.isSuccess()) {
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

    private fun prepareRetryConfig(retryPolicy: RetryPolicy, attemptIndex: Int): RetryConfig {
        var shouldRetry = false
        val newAttemptIndex: Int = attemptIndex + 1
        var minDelay = 0L
        var delay = 0L

        if (retryPolicy is RetryPolicy.InfinityExponential) {
            shouldRetry = true
            minDelay = retryPolicy.minDelay
        } else if (retryPolicy is RetryPolicy.Exponential) {
            shouldRetry = retryPolicy.retryCount > attemptIndex
            minDelay = retryPolicy.minDelay
        }

        if (shouldRetry) {
            delay = delayCalculator.countDelay(minDelay, newAttemptIndex)
        }

        return RetryConfig(shouldRetry, newAttemptIndex, delay)
    }
}