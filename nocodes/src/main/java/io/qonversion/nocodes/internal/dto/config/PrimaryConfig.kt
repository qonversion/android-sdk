package io.qonversion.nocodes.internal.dto.config

import io.qonversion.nocodes.BuildConfig

internal data class PrimaryConfig(
    val projectKey: String,
    val sdkVersion: String = BuildConfig.VERSION_NAME
)
