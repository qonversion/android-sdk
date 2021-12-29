package com.qonversion.android.sdk.internal.logger

import android.util.Log

internal class ConsoleLogger(config: LoggerConfig) : Logger {
    private val logLevel = config.logLevel
    private val tag = config.logTag

    override fun verbose(message: String) {
        if (logLevel == LogLevel.Verbose) {
            Log.println(Log.VERBOSE, tag, format(message))
        }
    }

    override fun info(message: String) {
        if (logLevel <= LogLevel.Info) {
            Log.println(Log.INFO, tag, format(message))
        }
    }

    override fun warn(message: String) {
        if (logLevel <= LogLevel.Warning) {
            Log.println(Log.WARN, tag, format(message))
        }
    }

    override fun error(message: String) {
        if (logLevel <= LogLevel.Error) {
            Log.println(Log.ERROR, tag, format(message))
        }
    }

    private fun format(message: String): String {
        return "Thread - " + Thread.currentThread().name + " " + message
    }
}
