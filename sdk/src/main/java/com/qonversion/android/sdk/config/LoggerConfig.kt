package com.qonversion.android.sdk.config

import com.qonversion.android.sdk.dto.LogLevel

data class LoggerConfig(
    val logLevel: LogLevel,
    val logTag: String
)
