package io.qonversion.android.sdk.internal.dto.config

import io.qonversion.android.sdk.BuildConfig
import io.qonversion.android.sdk.dto.QEnvironment
import io.qonversion.android.sdk.dto.QLaunchMode

internal data class PrimaryConfig(
    val projectKey: String,
    val launchMode: QLaunchMode,
    val environment: QEnvironment,
    val proxyUrl: String? = null,
    val isKidsMode: Boolean = false,
    val sdkVersion: String = BuildConfig.VERSION_NAME
)
