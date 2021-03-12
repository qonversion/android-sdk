package com.qonversion.android.sdk.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class HeadersInterceptor @Inject constructor(
    private val headersProvider: ApiHeadersProvider
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        request = request.newBuilder()
            .headers(headersProvider.getHeaders())
            .build()
        return chain.proceed(request)
    }
}