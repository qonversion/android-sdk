package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.dto.LogLevel

internal class ConsoleLogger(config: LoggerConfig) : Logger {
    private val logLevel = config.logLevel.level
    private val tag = config.logTag

    override fun verbose(message: String) {
        if (logLevel == LogLevel.Verbose.level) {
            log(LogLevel.Verbose, message)
        }
    }

    override fun info(message: String) {
        if (logLevel <= LogLevel.Info.level) {
            log(LogLevel.Info, message)
        }
    }

    override fun warn(message: String) {
        if (logLevel <= LogLevel.Warning.level) {
            log(LogLevel.Warning, message)
        }
    }

    override fun error(message: String) {
        if (logLevel <= LogLevel.Error.level) {
            log(LogLevel.Error, message)
        }
    }

    private fun log(level: LogLevel, message: String) {
        val androidLogLevel = when (level) {
            LogLevel.Verbose -> Log.VERBOSE
            LogLevel.Info -> Log.INFO
            LogLevel.Warning -> Log.WARN
            LogLevel.Error -> Log.ERROR
            else -> null
        }
        if (androidLogLevel != null) {
            Log.println(androidLogLevel, tag, format(message))
        }
    }

    private fun format(message: String): String {
        return "Thread - " + Thread.currentThread().name + " " + message
    }
}
