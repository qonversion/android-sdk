package com.qonversion.android.sdk.internal.api

import okhttp3.Request

internal class ApiHelper(apiUrl: String) {

    private val v0MethodsRegex = "${apiUrl}(?!v\\d+/).*"
    private val v1MethodsRegex = "${apiUrl}v1/.*"

    fun isDeprecatedEndpoint(request: Request) = isV0Request(request) || isV1Request(request)

    fun isV0Request(request: Request) = checkRequestVersion(request, v0MethodsRegex)

    fun isV1Request(request: Request) = checkRequestVersion(request, v1MethodsRegex)

    private fun checkRequestVersion(request: Request, regexStr: String): Boolean {
        val regex = Regex(regexStr)
        val results = regex.findAll(request.url().toString(), 0)

        return results.any()
    }
}
