package com.qonversion.android.sdk.purchasequeue


import okhttp3.*

class ForbiddenServerSimulation : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()
        val code = 403
        val responseString = ""

        return chain.proceed(request)
            .newBuilder()
            .code(code)
            .protocol(Protocol.HTTP_2)
            .message(responseString)
            .body(
                ResponseBody.create(
                    MediaType.parse("application/json"),
                    responseString.toByteArray()))
            .addHeader("content-type", "application/json")
            .build()
    }
}
