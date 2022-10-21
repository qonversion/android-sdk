package com.qonversion.android.sdk.internal.api

import com.qonversion.android.sdk.internal.HttpError
import com.qonversion.android.sdk.internal.QonversionConfig
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import javax.inject.Inject

internal class NetworkInterceptor @Inject constructor(
    private val headersProvider: ApiHeadersProvider,
    private val apiHelper: ApiHelper,
    private val config: QonversionConfig
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val fatalError = config.fatalError

        return if (fatalError != null) {
            Response.Builder()
                .code(fatalError.code)
                .body(ResponseBody.create(null, ""))
                .protocol(Protocol.HTTP_2)
                .message(fatalError.message)
                .request(chain.request())
                .build()
        } else {
            var request = chain.request()
            request = request.newBuilder()
                .headers(headersProvider.getHeaders())
                .build()

            val response = chain.proceed(request)
            if (response.code() in FATAL_ERRORS && apiHelper.isV1Request(request)) {
                config.fatalError = HttpError(response.code(), response.message())
            }

            return response
        }
    }

    companion object {
        private val FATAL_ERRORS = listOf(401, 402, 403)
    }
}
