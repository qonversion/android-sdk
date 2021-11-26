package com.qonversion.android.sdk.internal.networkLayer.headerBuilder

import android.os.Build
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.common.DEBUG_MODE_PREFIX
import com.qonversion.android.sdk.internal.common.PLATFORM
import com.qonversion.android.sdk.internal.common.PREFS_SOURCE_KEY
import com.qonversion.android.sdk.internal.common.PREFS_SOURCE_VERSION_KEY
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.networkLayer.ApiHeader
import java.util.Locale

class HeaderBuilderImpl internal constructor(
    private val localStorage: LocalStorage,
    private val locale: Locale,
    private val config: InternalConfig
): HeaderBuilder {
    override fun buildCommonHeaders(): Map<String, String> {
        val locale = locale.language
        val projectKey = if (config.debugMode) "$DEBUG_MODE_PREFIX${config.projectKey}" else config.projectKey
        val bearer = "Bearer $projectKey"
        val source = localStorage.getString(PREFS_SOURCE_KEY, PLATFORM)
        val sourceVersion = localStorage.getString(PREFS_SOURCE_VERSION_KEY, config.sdkVersion)

        return mapOf(
            ApiHeader.Authorization.value to bearer,
            ApiHeader.Locale.value to locale,
            ApiHeader.Source.value to source,
            ApiHeader.SourceVersion.value to sourceVersion,
            ApiHeader.Platform.value to PLATFORM,
            ApiHeader.PlatformVersion.value to Build.VERSION.RELEASE,
            ApiHeader.UserID.value to config.uid)
    }
}