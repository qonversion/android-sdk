package com.qonversion.android.sdk.internal.dto.config

import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode

internal data class PrimaryConfig(
    val projectKey: String,
    val launchMode: LaunchMode,
    val environment: Environment,
    val sdkVersion: String = BuildConfig.VERSION_NAME
)
