package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.dto.LogLevel

internal class ConsoleLogger(config: LoggerConfig) : Logger {
    private val logLevel = config.logLevel.level
    private val tag = config.logTag

    override fun verbose(message: String) {
        if (logLevel == LogLevel.Verbose.level) {
            Log.v(tag, format(message))
        }
    }

    override fun info(message: String) {
        if (logLevel <= LogLevel.Info.level) {
            Log.i(tag, format(message))
        }
    }

    override fun warn(message: String) {
        if (logLevel <= LogLevel.Warning.level) {
            Log.w(tag, format(message))
        }
    }

    override fun error(message: String, throwable: Throwable?) {
        if (logLevel <= LogLevel.Error.level) {
            Log.e(tag, format(message), throwable)
        }
    }

    private fun format(message: String): String {
        return "Thread - " + Thread.currentThread().name + " " + message
    }
}
