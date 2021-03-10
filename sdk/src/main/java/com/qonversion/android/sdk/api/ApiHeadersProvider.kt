package com.qonversion.android.sdk.api

import android.os.Build
import com.qonversion.android.sdk.Constants.PREFS_PREFIX
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.storage.SharedPreferencesCache
import java.util.*
import javax.inject.Inject

class ApiHeadersProvider @Inject constructor(
    private val config: QonversionConfig,
    private val sharedPreferencesCache: SharedPreferencesCache
) {
    private val projectKey: String
    private fun getLocale() = Locale.getDefault().language

    init {
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
        AUTHORIZATION to getBearer(projectKey),
        SOURCE to getSource(),
        SOURCE_VERSION to getSourceVersion(),
        PLATFORM to ANDROID_PLATFORM,
        PLATFORM_VERSION to Build.VERSION.RELEASE
    )

    private fun getSource() =
        sharedPreferencesCache.getString(PREFS_SOURCE_KEY, null) ?: ANDROID_PLATFORM

    private fun getSourceVersion() =
        sharedPreferencesCache.getString(PREFS_SOURCE_VERSION_KEY, null) ?: config.sdkVersion

    companion object{
        const val ANDROID_PLATFORM = "android"

        const val PREFS_SOURCE_KEY = "$PREFS_PREFIX.source"
        const val PREFS_SOURCE_VERSION_KEY = "$PREFS_PREFIX.sourceVersion"

        const val DEBUG_MODE_KEY = "test_"
        fun getBearer(projectKey: String) = "Bearer $projectKey"

        // Headers
        const val CONTENT_TYPE = "Content-Type"
        const val AUTHORIZATION = "Authorization"
        const val USER_LOCALE = "User-Locale"
        const val SOURCE = "Source"
        const val SOURCE_VERSION = "Source-Version"
        const val PLATFORM = "Platform"
        const val PLATFORM_VERSION = "Platform-Version"
    }
}

sealed class ApiHeaders : HashMap<String, String>() {
    class Screens : ApiHeaders()
    class Default : ApiHeaders()
}