package com.qonversion.android.sdk.internal.api

import android.os.Build
import com.qonversion.android.sdk.internal.Constants.PREFS_PREFIX
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import okhttp3.Headers
import java.util.*
import javax.inject.Inject

internal class ApiHeadersProvider @Inject constructor(
    private val config: InternalConfig,
    private val sharedPreferencesCache: SharedPreferencesCache
) {
    private val projectKey: String = if (config.isSandbox) {
        "$DEBUG_MODE_KEY${config.primaryConfig.projectKey}"
    } else {
        config.primaryConfig.projectKey
    }

    private fun getLocale() = Locale.getDefault().language

    fun getHeaders(): Headers {
        val headerBuilder = Headers.Builder()
        for ((key, value) in getHeadersMap()) {
            headerBuilder.add(key, value)
        }

        return headerBuilder.build()
    }

    private fun getHeadersMap() = mapOf(
        CONTENT_TYPE to "application/json",
        AUTHORIZATION to getBearer(getProjectKey()),
        USER_LOCALE to getLocale(),
        SOURCE to getSource(),
        SOURCE_VERSION to getSourceVersion(),
        PLATFORM to getPlatform(),
        PLATFORM_VERSION to getPlatformVersion(),
        UID to config.uid
    )

    fun getProjectKey() = projectKey

    fun getPlatform() = ANDROID_PLATFORM

    fun getPlatformVersion(): String = Build.VERSION.RELEASE

    fun getSource() =
        sharedPreferencesCache.getString(PREFS_SOURCE_KEY, null) ?: ANDROID_PLATFORM

    fun getSourceVersion() =
        sharedPreferencesCache.getString(PREFS_SOURCE_VERSION_KEY, null) ?: config.primaryConfig.sdkVersion

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
    }
}
