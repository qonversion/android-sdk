package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.provider.LoggerConfigProvider

internal class ConsoleLogger(private val configProvider: LoggerConfigProvider) : Logger {
    private val logLevel get() = configProvider.loggerConfig.logLevel.level
    private val tag get() = configProvider.loggerConfig.logTag

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
