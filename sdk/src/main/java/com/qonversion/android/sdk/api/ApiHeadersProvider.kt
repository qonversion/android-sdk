package com.qonversion.android.sdk.api

import android.os.Build
import com.qonversion.android.sdk.Constants.PREFS_PREFIX
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.storage.SharedPreferencesCache
import okhttp3.Headers
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

    fun getHeaders(): Headers {
        val headerBuilder = Headers.Builder()
        for ((key, value) in getHeadersMap()) {
            headerBuilder.add(key, value)
        }

        return headerBuilder.build()
    }

    private fun getHeadersMap() = mapOf(
        CONTENT_TYPE to "application/json",
        AUTHORIZATION to getBearer(projectKey),
        USER_LOCALE to getLocale(),
        SOURCE to getSource(),
        SOURCE_VERSION to getSourceVersion(),
        PLATFORM to ANDROID_PLATFORM,
        PLATFORM_VERSION to Build.VERSION.RELEASE,
        UID to config.uid,
        API_MINOR_VERSION to "2"
    )

    private fun getSource() =
        sharedPreferencesCache.getString(PREFS_SOURCE_KEY, null) ?: ANDROID_PLATFORM

    private fun getSourceVersion() =
        sharedPreferencesCache.getString(PREFS_SOURCE_VERSION_KEY, null) ?: config.sdkVersion

    companion object {
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
        const val UID = "User-Id"
        const val API_MINOR_VERSION = "API-Minor-Version"
    }
}
