package com.qonversion.android.sdk.internal.logger

import com.qonversion.android.sdk.dto.LogLevel

private const val LOG_TAG = "Qonversion"

internal data class LoggerConfig(
    val logLevel: LogLevel = LogLevel.Info,
    val logTag: String = LOG_TAG
)
