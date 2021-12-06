package com.qonversion.android.sdk.internal.networkLayer.dto

internal sealed class Response(
    val code: Int
) {
    class Error(
        code: Int,
        val message: String,
        val type: String? = null,
        val apiCode: String? = null
    ) : Response(code)

    class Success(code: Int, val data: Any) : Response(code)
}
