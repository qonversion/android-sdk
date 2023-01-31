package com.qonversion.android.sdk.internal.dto.config

import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode

internal data class PrimaryConfig(
    val projectKey: String,
    val launchMode: QLaunchMode,
    val environment: QEnvironment,
    val proxyUrl: String? = null,
    val sdkVersion: String = BuildConfig.VERSION_NAME
)
