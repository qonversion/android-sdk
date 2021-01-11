package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.di.QDependencyInjector
import java.util.*
import kotlin.collections.HashMap

class ApiHeadersProvider {
    private val projectKey: String
    private fun getLocale() = Locale.getDefault().language

    init {
        val config = QDependencyInjector.appComponent.config()
        projectKey = if (config.isDebugMode) "$DEBUG_MODE_KEY${config.key}" else config.key
    }

    fun getScreenHeaders(): ApiHeaders.Screens =
        ApiHeaders.Screens().apply {
            putAll(getHeaders())
            put(USER_LOCALE, getLocale())
        }

    fun getHeaders(): ApiHeaders.Default =
        ApiHeaders.Default().apply {
            putAll(getDefaultHeaders())
        }

    private fun getDefaultHeaders() = mapOf(
        CONTENT_TYPE to "application/json",
        AUTHORIZATION to getBearer(projectKey)
    )

    companion object {
        private const val CONTENT_TYPE = "Content-Type"
        private const val AUTHORIZATION = "Authorization"
        private const val USER_LOCALE = "User-Locale"
        private const val DEBUG_MODE_KEY = "test_"
        private fun getBearer(projectKey: String) = "Bearer $projectKey"
    }
}

sealed class ApiHeaders : HashMap<String, String>() {
    class Screens : ApiHeaders()
    class Default : ApiHeaders()
}
