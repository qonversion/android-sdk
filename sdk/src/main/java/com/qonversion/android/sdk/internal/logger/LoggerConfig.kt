package com.qonversion.android.sdk.internal.logger

import com.qonversion.android.sdk.dto.LogLevel

data class LoggerConfig(
    val logLevel: LogLevel,
    val logTag: String
)
