package io.qonversion.nocodes.internal.dto.config

import io.qonversion.nocodes.BuildConfig
import io.qonversion.nocodes.internal.common.FallbackConstants

internal data class PrimaryConfig(
    val projectKey: String,
    val sdkVersion: String = BuildConfig.VERSION_NAME,
    val customFallbackFileName: String? = null
) {
    val effectiveFallbackFileName: String
        get() = customFallbackFileName ?: FallbackConstants.DEFAULT_FILE_NAME
}
