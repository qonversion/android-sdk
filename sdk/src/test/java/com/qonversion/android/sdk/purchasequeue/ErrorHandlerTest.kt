package com.qonversion.android.sdk.purchasequeue

import com.qonversion.android.sdk.ErrorHandler
import com.qonversion.android.sdk.PurchaseSendingQueue
import com.qonversion.android.sdk.api.RetrofitException
import com.qonversion.android.sdk.logger.StubLogger
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class ErrorHandlerTest {

    private lateinit var errorHandler: ErrorHandler

    @Before
    fun setup() {
        errorHandler = ErrorHandler(StubLogger())
    }

    @Test
    fun handleRuntimeError() {
        val state = errorHandler.handle(RuntimeException("test runtime exception"))
        Assert.assertEquals(state, PurchaseSendingQueue.State.SERVER_ERROR)
    }

    @Test
    fun handleNoInternetConnectionError() {
        val state = errorHandler.handle(UnknownHostException())
        Assert.assertEquals(state, PurchaseSendingQueue.State.SERVER_ERROR)
    }

    @Test
    fun handlePurchaseDuplicationError() {
        val duplicationResponse: Response<String> = Response.error(400, ResponseBody.create(
            MediaType.parse("application/json"),
            ALREADY_BEEN_ADDED_RESPONSE.toByteArray()))

        val state = errorHandler.handle(
            RetrofitException.httpError(
               url = "https://mock.com",
               response = duplicationResponse,
               httpException = HttpException(duplicationResponse)
            )
        )
        Assert.assertEquals(state, PurchaseSendingQueue.State.DUPLICATE)
    }

    @Test
    fun handleAnotherRetrofitError() {
        val errorResponse: Response<String> = Response.error(400, ResponseBody.create(
            MediaType.parse("application/json"), ""))

        val state = errorHandler.handle(
            RetrofitException.httpError(
                url = "https://mock.com",
                response = errorResponse,
                httpException = HttpException(errorResponse)
            )
        )
        Assert.assertEquals(state, PurchaseSendingQueue.State.SERVER_ERROR)
    }
}