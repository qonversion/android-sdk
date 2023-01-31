package com.qonversion.android.sdk.internal.api

import okhttp3.Request

internal class ApiHelper(apiUrl: String) {

    private val v1MethodsRegex = "${apiUrl}v1/.*"

    fun isV1Request(request: Request): Boolean {
        val regex = Regex(v1MethodsRegex)
        val results = regex.findAll(request.url().toString(), 0)

        return results.any()
    }
}
