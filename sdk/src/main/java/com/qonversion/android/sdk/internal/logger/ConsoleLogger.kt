package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.BuildConfig

internal class ConsoleLogger : Logger {
    override fun release(message: String?) {
        log(TAG, message)
    }

    override fun debug(message: String?) {
        if (BuildConfig.DEBUG) {
            log(TAG, message)
        }
    }

    override fun debug(tag: String?, message: String?) {
        if (BuildConfig.DEBUG) {
            log(tag, message)
        }
    }

    private fun log(tag: String?, message: String?) {
        Log.println(Log.DEBUG, tag, format(message))
    }

    private fun format(message: String?): String {
        return "Thread - " + Thread.currentThread().name + " " + message
    }

    companion object {
        private const val TAG = "Qonversion"
    }
}
