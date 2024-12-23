package io.qonversion.nocodes.internal.networkLayer.apiInteractor

import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.mappers.error.ErrorResponseMapper
import io.qonversion.nocodes.internal.networkLayer.RetryPolicy
import io.qonversion.nocodes.internal.networkLayer.dto.RawResponse
import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.dto.Response
import io.qonversion.nocodes.internal.networkLayer.networkClient.NetworkClient
import io.qonversion.nocodes.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import io.qonversion.nocodes.internal.provider.NetworkConfigHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

private val ERROR_CODES_BLOCKING_FURTHER_EXECUTIONS = listOf(
    HttpURLConnection.HTTP_UNAUTHORIZED,
    HttpURLConnection.HTTP_PAYMENT_REQUIRED,
    418 // "I'm a teapot", for possible api usage.
)

internal data class RetryConfig(
    internal val shouldRetry: Boolean,
    internal val attemptIndex: Int,
    internal val delay: Long
)

internal class ApiInteractorImpl(
    private val networkClient: NetworkClient,
    private val delayCalculator: RetryDelayCalculator,
    private val configHolder: NetworkConfigHolder,
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
            if (!configHolder.canSendRequests) {
                throw NoCodesException(ErrorCode.RequestDenied)
            }

            var executionException: NoCodesException? = null
            val response = try {
                networkClient.execute(request)
            } catch (cause: NoCodesException) {
                if (cause.code === ErrorCode.BadNetworkRequest) {
                    throw cause
                }
                executionException = cause
                null
            }

            if (response?.isSuccess == true) {
                val data = response.getResponsePayload()["data"]
                    ?: throw NoCodesException(
                        ErrorCode.BadResponse,
                        "No data provided in response"
                    )
                Response.Success(response.code, data)
            } else {
                if (response != null && ERROR_CODES_BLOCKING_FURTHER_EXECUTIONS.contains(response.code)) {
                    configHolder.canSendRequests = false
                    return@withContext getErrorResponse(response, executionException)
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
        executionException: NoCodesException?
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
            throw NoCodesException(
                ErrorCode.BadResponse,
                "Unexpected payload type. Map expected",
                cause = cause
            )
        }
    }
}