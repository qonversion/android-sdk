package com.qonversion.android.sdk.internal.networkLayer.dto

internal sealed class Response(
    val code: Int
) {
    class Error(
        code: Int,
        val type: String?,
        val apiCode: String?,
        val message: String?
    ): Response(code)

    class Success(code: Int, val data: Any): Response(code)
}
