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

internal class HeaderBuilderImpl(
    private val localStorage: LocalStorage,
    private val locale: Locale,
    private val config: InternalConfig
) : HeaderBuilder {
    private val source by lazy { localStorage.getString(PREFS_SOURCE_KEY, PLATFORM) }
    private val sourceVersion by lazy { localStorage.getString(PREFS_SOURCE_VERSION_KEY, config.sdkVersion) }

    override fun buildCommonHeaders(): Map<String, String> {
        val locale = locale.language
        val projectKey = if (config.debugMode) "$DEBUG_MODE_PREFIX${config.projectKey}" else config.projectKey
        val bearer = "Bearer $projectKey"

        return mapOf(
            ApiHeader.Authorization.key to bearer,
            ApiHeader.Locale.key to locale,
            ApiHeader.Source.key to source,
            ApiHeader.SourceVersion.key to sourceVersion,
            ApiHeader.Platform.key to PLATFORM,
            ApiHeader.PlatformVersion.key to Build.VERSION.RELEASE,
            ApiHeader.UserID.key to config.uid)
    }
}
