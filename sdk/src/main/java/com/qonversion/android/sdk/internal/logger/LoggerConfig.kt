package com.qonversion.android.sdk.internal.logger

private const val LOG_TAG = "Qonversion"

data class LoggerConfig(
    val logLevel: LogLevel = LogLevel.Info,
    val logTag: String = LOG_TAG
)
