package com.qonversion.android.sdk.purchasequeue


import okhttp3.*
import okio.Buffer

class StableServerSimulation : Interceptor {

    private val serverDb : MutableSet<String> = HashSet()

    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()
        val content = parseRequestBody(request)
        val code: Int
        val responseString: String
        if (serverDb.contains(content)) {
            responseString = ALREADY_BEEN_ADDED_RESPONSE
            code = 400
        } else {
            responseString = SUCCESS_RESPONSE
            code = 200
            serverDb.add(content)
        }

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

    private fun parseRequestBody(request: Request): String {
        val bufer: okio.Buffer = Buffer()
        request.body()?.writeTo(bufer)
        return bufer.readUtf8()
    }
}

const val SUCCESS_RESPONSE = """
{
  "success": true,
  "data": {
    "client_uid": "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
    "sdk_version": "1.0.5",
    "custom_uid": null,
    "device_id": "efa06e4d1c84bd66",
    "locale": "ru",
    "model": "moto g(7) plus",
    "os_name": "Android",
    "os_version": "28",
    "tracking_enabled": true,
    "client_advertiser_id": null,
    "client_id": 10352867
  }
}
"""

const val ALREADY_BEEN_ADDED_RESPONSE = """
{
  "success": false,
  "data": {
    "name": "Bad Request",
    "message": "This original_transaction_id and product_id has already been added.",
    "code": 0,
    "status": 400
  }
}
"""