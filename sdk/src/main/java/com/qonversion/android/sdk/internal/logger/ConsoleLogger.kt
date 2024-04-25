package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.BuildConfig

internal class ConsoleLogger : Logger {
    override fun error(message: String) {
        log(Log.ERROR, message)
    }

    override fun warn(message: String) {
        log(Log.WARN, message)
    }

    override fun release(message: String) {
        log(Log.INFO, message)
    }

    override fun debug(message: String) {
        if (BuildConfig.DEBUG) {
            log(Log.DEBUG, message)
        }
    }

    private fun log(logLevel: Int, message: String) {
        Log.println(logLevel, TAG, format(message))
    }

    private fun format(message: String): String {
        return "[Thread - " + Thread.currentThread().name + "] " + message
    }

    companion object {
        private const val TAG = "Qonversion"
    }
}
