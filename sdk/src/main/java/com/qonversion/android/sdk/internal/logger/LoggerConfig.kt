package com.qonversion.android.sdk.internal.logger

import com.qonversion.android.sdk.dto.LogLevel

internal data class LoggerConfig(
    val logLevel: LogLevel = LogLevel.Info,
    val logTag: String = ""
)
