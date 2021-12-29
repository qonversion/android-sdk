package com.qonversion.android.sdk.internal.logger

import android.util.Log

private const val TAG = "Qonversion"

internal class ConsoleLogger(config: LoggerConfig) : Logger {
    private val logLevel = config.logLevel

    override fun verbose(message: String) {
        if (logLevel == LogLevel.Verbose) {
            Log.println(Log.VERBOSE, TAG, format(message))
        }
    }

    override fun info(message: String) {
        if (logLevel <= LogLevel.Info) {
            Log.println(Log.INFO, TAG, format(message))
        }
    }

    override fun warn(message: String) {
        if (logLevel <= LogLevel.Warning) {
            Log.println(Log.WARN, TAG, format(message))
        }
    }

    override fun error(message: String) {
        if (logLevel <= LogLevel.Error) {
            Log.println(Log.ERROR, TAG, format(message))
        }
    }

    private fun format(message: String): String {
        return "Thread - " + Thread.currentThread().name + " " + message
    }
}
