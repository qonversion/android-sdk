package com.qonversion.android.sdk.internal.networkLayer.apiInteractor

import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
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
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
internal class ApiInteractorTest {

    private lateinit var interactor: ApiInteractorImpl
    private val networkClient = mockk<NetworkClient>()
    private val delayCalculator = mockk<RetryDelayCalculator>()
    private val config = mockk<InternalConfig>()
    private val errorMapper = mockk<ErrorResponseMapper>()
    private val request = Request.get("smth", emptyMap())

    private val successCode = 200
    private val successData = mapOf("key" to "value")
    private val successPayload = mapOf("data" to successData)
    private val rawSuccessResponse = RawResponse(successCode, successPayload)

    private val errorCode = 500
    private val errorMessage = "Test message"
    private val errorData = mapOf("message" to errorMessage)
    private val errorPayload = mapOf("error" to errorData)
    private val rawErrorResponse = RawResponse(errorCode, errorPayload)
    private val parsedErrorResponse = Response.Error(errorCode, "Test error")

    @Before
    fun setUp() {
        interactor = ApiInteractorImpl(networkClient, delayCalculator, config, errorMapper)

        val minDelaySlot = slot<Long>()
        every {
            delayCalculator.countDelay(capture(minDelaySlot), any())
        } answers { minDelaySlot.captured + 1 }
        every {
            config.requestsShouldBeDenied
        } returns false
        coEvery {
            networkClient.execute(request)
        } returns rawSuccessResponse
        every {
            errorMapper.fromMap(errorData, errorCode)
        } returns parsedErrorResponse
    }

    @Test
    fun `no retry after first attempt for retry policy none`() {
        // given
        val retryPolicy = RetryPolicy.None

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isFalse
        verify(exactly = 0) { delayCalculator.countDelay(any(), any()) }
    }

    @Test
    fun `no retry after several attempts for retry policy none`() {
        // given
        val retryPolicy = RetryPolicy.None

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 10)

        // then
        assertThat(retryConfig.shouldRetry).isFalse
        verify(exactly = 0) { delayCalculator.countDelay(any(), any()) }
    }

    @Test
    fun `retry after first attempt for infinite exponential retry policy`() {
        // given
        val retryPolicy = RetryPolicy.InfiniteExponential()

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isTrue
        assertThat(retryConfig.attemptIndex).isEqualTo(1)
        assertThat(retryConfig.delay >= retryPolicy.minDelay)
    }

    @Test
    fun `retry after several attempts for infinite exponential retry policy`() {
        // given
        val attemptIndex = 5
        val retryPolicy = RetryPolicy.InfiniteExponential()

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, attemptIndex)

        // then
        assertThat(retryConfig.shouldRetry).isTrue
        assertThat(retryConfig.attemptIndex).isEqualTo(attemptIndex + 1)
        assertThat(retryConfig.delay >= retryPolicy.minDelay)
    }

    @Test
    fun `retry for infinite exponential retry policy with min delay`() {
        // given
        val minDelay = 500000L
        val retryPolicy = RetryPolicy.InfiniteExponential(minDelay)

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isTrue
        assertThat(retryConfig.delay >= minDelay)
    }

    @Test
    fun `no retry for infinite exponential retry policy with negative min delay`() {
        // given
        val minDelay = -100000L
        val retryPolicy = RetryPolicy.InfiniteExponential(minDelay = minDelay)

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isFalse
        verify(exactly = 0) { delayCalculator.countDelay(any(), any()) }
    }

    @Test
    fun `retry after first attempt for exponential retry policy`() {
        // given
        val retryPolicy = RetryPolicy.Exponential()

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isTrue
        assertThat(retryConfig.attemptIndex).isEqualTo(1)
        assertThat(retryConfig.delay >= retryPolicy.minDelay)
    }

    @Test
    fun `penultimate retry for exponential retry policy`() {
        // given
        val attemptIndex = 5
        val retryPolicy = RetryPolicy.Exponential(attemptIndex + 1)

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, attemptIndex)

        // then
        assertThat(retryConfig.shouldRetry).isTrue
        assertThat(retryConfig.attemptIndex).isEqualTo(attemptIndex + 1)
        assertThat(retryConfig.delay >= retryPolicy.minDelay)
    }

    @Test
    fun `last retry for exponential retry policy`() {
        // given
        val attemptIndex = 5
        val retryPolicy = RetryPolicy.Exponential(attemptIndex)

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, attemptIndex)

        // then
        assertThat(retryConfig.shouldRetry).isFalse
        verify(exactly = 0) { delayCalculator.countDelay(any(), any()) }
    }

    @Test
    fun `retry for exponential retry policy with min delay`() {
        // given
        val minDelay = 500000L
        val retryPolicy = RetryPolicy.Exponential(minDelay = minDelay)

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isTrue
        assertThat(retryConfig.delay >= minDelay)
    }

    @Test
    fun `no retry for exponential retry policy with negative min delay`() {
        // given
        val minDelay = -100000L
        val retryPolicy = RetryPolicy.Exponential(minDelay = minDelay)

        // when
        val retryConfig = interactor.prepareRetryConfig(retryPolicy, 0)

        // then
        assertThat(retryConfig.shouldRetry).isFalse
        verify(exactly = 0) { delayCalculator.countDelay(any(), any()) }
    }

    @Test
    fun `get error from correct api response`() {
        // given

        // when
        val errorResponse = interactor.getErrorResponse(rawErrorResponse, null)

        // then
        assertThat(errorResponse).isEqualTo(parsedErrorResponse)
    }

    @Test
    fun `get error from correct api response without data`() {
        // given
        val rawResponse = RawResponse(errorCode, mapOf<Any?, Any?>())

        // when
        val errorResponse = interactor.getErrorResponse(rawResponse, null)

        // then
        assertThat(errorResponse.code).isEqualTo(errorCode)
    }

    @Test
    fun `get error from exception`() {
        // given
        val exception = QonversionException(ErrorCode.NetworkRequestExecution)

        // when
        assertThatThrownBy {
            interactor.getErrorResponse(null, exception)
        }.isEqualTo(exception)
    }

    @Test
    fun `get error from nothing`() {
        // given

        // when
        assertThatThrownBy {
            interactor.getErrorResponse(null, null)
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `execute request with deny option on`() {
        // given
        every {
            config.requestsShouldBeDenied
        } returns true

        // when and then
        coAssertThatQonversionExceptionThrown(ErrorCode.RequestDenied) {
            interactor.execute(request)
        }
    }

    @Test
    fun `network client throws non retryable exception`() {
        // given
        val exceptedException = QonversionException(ErrorCode.BadNetworkRequest)
        coEvery {
            networkClient.execute(request)
        }.throws(exceptedException)

        // when
        val exception = try {
            runTest {
                interactor.execute(request)
            }
            null
        } catch (e: QonversionException) {
            e
        }

        // then
        assertThat(exception).isEqualTo(exceptedException)
        coVerify(exactly = 1) { networkClient.execute(request) }
    }

    @Test
    fun `network client throws retryable exception`() {
        // given
        val exceptedException = QonversionException(ErrorCode.NetworkRequestExecution)
        val retryCount = 2;
        coEvery {
            networkClient.execute(request)
        }.throws(exceptedException)

        // when
        val exception = try {
            runTest {
                interactor.execute(request, RetryPolicy.Exponential(retryCount))
            }
            null
        } catch (e: QonversionException) {
            e
        }

        // then
        assertThat(exception).isEqualTo(exceptedException)
        coVerify(exactly = retryCount + 1) { networkClient.execute(request) }
    }

    @Test
    fun `network client returns non-api payload`() {
        // given
        coEvery {
            networkClient.execute(request)
        } returns RawResponse(successCode, "Success")

        // when and then
        coAssertThatQonversionExceptionThrown(ErrorCode.BadResponse) {
            interactor.execute(request)
        }
    }

    @Test
    fun `network client returns success payload without data`() {
        // given
        coEvery {
            networkClient.execute(request)
        } returns RawResponse(successCode, mapOf("Success" to "Or not?"))

        // when and then
        coAssertThatQonversionExceptionThrown(ErrorCode.BadResponse) {
            interactor.execute(request)
        }
    }

    @Test
    fun `execute with successful response`() = runTest {
        // given

        // when
        val response = interactor.execute(request)

        // then
        assertThatIsSuccessResponse(response)
    }

    @Test
    fun `error response without retry`() = runTest  {
        // given
        coEvery {
            networkClient.execute(request)
        } returns rawErrorResponse

        // when
        val response = interactor.execute(request, RetryPolicy.None)

        // then
        assertThat(response).isEqualTo(parsedErrorResponse)
        coVerify(exactly = 1) {
            networkClient.execute(any())
        }
    }

    @Test
    fun `error response with limited retry`() = runTest {
        // given
        coEvery {
            networkClient.execute(request)
        } returns rawErrorResponse

        // when
        val response = interactor.execute(request, RetryPolicy.Exponential(2))

        // then
        assertThat(response).isEqualTo(parsedErrorResponse)
        coVerify(exactly = 3) {
            networkClient.execute(any())
        }
    }

    @Test
    fun `error response which shouldn't be retried`() = runTest {
        // given
        val nonRetryableErrorCode = 400
        val errorResponse = RawResponse(nonRetryableErrorCode, errorPayload)
        val parsedErrorResponse = Response.Error(nonRetryableErrorCode, "Test error")
        coEvery {
            networkClient.execute(request)
        } returns errorResponse
        every {
            errorMapper.fromMap(errorData, nonRetryableErrorCode)
        } returns parsedErrorResponse

        // when
        val response = interactor.execute(request, RetryPolicy.Exponential(2))

        // then
        assertThat(response).isEqualTo(parsedErrorResponse)
        coVerify(exactly = 1) {
            networkClient.execute(any())
        }
    }

    @Test
    fun `error response with limited retry and success at the end`() = runTest {
        // given
        val errorCount = 4
        coEvery {
            networkClient.execute(request)
        } returnsMany List(errorCount) { rawErrorResponse } andThen rawSuccessResponse

        // when
        val response = interactor.execute(request, RetryPolicy.Exponential(retryCount = errorCount + 1))

        // then
        assertThatIsSuccessResponse(response)
        coVerify(exactly = errorCount + 1) {
            networkClient.execute(any())
        }
    }

    @Test
    fun `error response with infinite retry and success at the end`() = runTest {
        // given
        val errorCount = 6
        coEvery {
            networkClient.execute(request)
        } returnsMany List(errorCount) { rawErrorResponse } andThen rawSuccessResponse

        // when
        val response = interactor.execute(request, RetryPolicy.InfiniteExponential())

        // then
        assertThatIsSuccessResponse(response)
        coVerify(exactly = errorCount + 1) {
            networkClient.execute(any())
        }
    }

    @Test
    fun `error response with code, blocking further executions`() = runTest {
        // given
        val blockingErrorCode = 401
        val errorResponse = RawResponse(blockingErrorCode, errorPayload)
        val parsedErrorResponse = Response.Error(blockingErrorCode, "Test error")
        coEvery {
            networkClient.execute(request)
        } returns errorResponse
        every {
            errorMapper.fromMap(errorData, blockingErrorCode)
        } returns parsedErrorResponse
        val setValue = slot<Boolean>()
        every {
            config.requestsShouldBeDenied = capture(setValue)
        } just runs

        // when
        val response = interactor.execute(request, RetryPolicy.InfiniteExponential())

        // then
        assertThat(response).isEqualTo(parsedErrorResponse)
        coVerify(exactly = 1) {
            networkClient.execute(any())
        }
        assertThat(setValue.captured).isTrue()
    }

    private fun assertThatIsSuccessResponse(response: Response) {
        assertThat(response).isInstanceOf(Response.Success::class.java)
        assertThat(response.code).isEqualTo(successCode)
        assertThat((response as Response.Success).data).isEqualTo(successData)
    }
}