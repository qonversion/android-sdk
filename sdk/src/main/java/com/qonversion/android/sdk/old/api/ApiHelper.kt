package com.qonversion.android.sdk.old.api

import com.qonversion.android.sdk.old.di.module.NetworkModule.Companion.BASE_URL
import okhttp3.Request

class ApiHelper {

    fun isV1Request(request: Request): Boolean {
        val regex = Regex(V1_METHODS_REGEX)
        val results = regex.findAll(request.url().toString(), 0)

        return results.any()
    }

    companion object {
        private const val V1_METHODS_REGEX = "${BASE_URL}v1/.*"
    }
}
