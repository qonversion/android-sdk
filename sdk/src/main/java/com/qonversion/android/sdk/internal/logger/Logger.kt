package com.qonversion.android.sdk.internal.logger

internal interface Logger {
    fun release(message: String?)

    fun debug(message: String?)

    fun debug(tag: String?, message: String?)
}
