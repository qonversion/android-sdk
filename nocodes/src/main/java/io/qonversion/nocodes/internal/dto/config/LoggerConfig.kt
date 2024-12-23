package io.qonversion.nocodes.internal.dto.config

import io.qonversion.nocodes.dto.LogLevel

data class LoggerConfig(
    val logLevel: LogLevel,
    val logTag: String
)
