package com.qonversion.android.sdk.internal.networkLayer.apiInteractor

import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.mappers.ErrorResponseMapper
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.dto.RawResponse
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.ClassCastException
import java.net.HttpURLConnection

private val ERROR_CODES_BLOCKING_FURTHER_EXECUTIONS = listOf(
    HttpURLConnection.HTTP_UNAUTHORIZED,
    HttpURLConnection.HTTP_PROXY_AUTH
)

internal data class RetryConfig(
    internal val shouldRetry: Boolean,
    internal val attemptIndex: Int = 0,
    internal val delay: Long = 0L
)

internal class ApiInteractorImpl(
    private val networkClient: NetworkClient,
    private val delayCalculator: RetryDelayCalculator,
    private val config: InternalConfig,
    private val errorMapper: ErrorResponseMapper,
    private val defaultRetryPolicy: RetryPolicy = RetryPolicy.Exponential()
) : ApiInteractor {
    override suspend fun execute(request: Request): Response {
        return execute(request, defaultRetryPolicy)
    }

    override suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response {
        return execute(request, retryPolicy, 0)
    }

    private suspend fun execute(
        request: Request,
        retryPolicy: RetryPolicy,
        attemptIndex: Int
    ): Response {
        return withContext(Dispatchers.IO) {
            if (config.requestsShouldBeDenied) {
                throw QonversionException(ErrorCode.RequestDenied)
            }

            var executionException: QonversionException? = null
            val response = try {
                networkClient.execute(request)
            } catch (cause: QonversionException) {
                if (cause.code === ErrorCode.BadNetworkRequest) {
                    throw cause
                }
                executionException = cause
                null
            }

            if (response?.isSuccess == true) {
                val data = response.getResponsePayload()["data"]
                    ?: throw QonversionException(
                        ErrorCode.BadResponse,
                        "No data provided in response"
                    )
                Response.Success(response.code, data)
            } else {
                if (response != null && ERROR_CODES_BLOCKING_FURTHER_EXECUTIONS.contains(response.code)) {
                    config.requestsShouldBeDenied = true
                }

                val shouldTryToRetry =
                    response?.isInternalServerError == true || executionException != null

                val retryConfig = if (shouldTryToRetry) {
                    prepareRetryConfig(retryPolicy, attemptIndex)
                } else {
                    null
                }

                if (retryConfig?.shouldRetry == true) {
                    delay(retryConfig.delay)
                    execute(request, retryPolicy, retryConfig.attemptIndex)
                } else {
                    getErrorResponse(response, executionException)
                }
            }
        }
    }

    internal fun getErrorResponse(
        response: RawResponse?,
        executionException: QonversionException?
    ): Response.Error {
        return when {
            response != null -> {
                val payload = response.getResponsePayload()
                val errorData = payload["error"] as? Map<*, *>
                errorData?.let {
                    errorMapper.fromMap(it, response.code)
                } ?: Response.Error(response.code, "No error data provided")
            }
            executionException != null -> {
                throw executionException
            }
            else -> {
                // Unacceptable state.
                throw IllegalStateException(
                    "Unreachable code. Either response or executionException should be non-null"
                )
            }
        }
    }

    internal fun prepareRetryConfig(retryPolicy: RetryPolicy, attemptIndex: Int): RetryConfig {
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

    private fun RawResponse.getResponsePayload(): Map<*, *> {
        return try {
            (payload as Map<*, *>)
        } catch (cause: ClassCastException) {
            throw QonversionException(
                ErrorCode.BadResponse,
                "Unexpected payload type. Map expected",
                cause = cause
            )
        }
    }
}
