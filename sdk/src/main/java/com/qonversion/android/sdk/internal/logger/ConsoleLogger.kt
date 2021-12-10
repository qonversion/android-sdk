package com.qonversion.android.sdk.internal.logger

import android.util.Log

private const val TAG = "Qonversion"

internal class ConsoleLogger(private val isDebug: Boolean) : Logger {
    override fun release(message: String) {
        log(TAG, message)
    }

    override fun debug(message: String) {
        debug(TAG, message)
    }

    override fun debug(tag: String, message: String) {
        if (isDebug) {
            log(tag, message)
        }
    }

    private fun log(tag: String, message: String) {
        Log.println(Log.DEBUG, tag, format(message))
    }

    private fun format(message: String): String {
        return "Thread - " + Thread.currentThread().name + " " + message
    }
}