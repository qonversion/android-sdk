package com.qonversion.android.sdk.internal.logger

internal interface Logger {

    fun error(message: String)

    fun warn(message: String)

    fun release(message: String)

    fun debug(message: String)
}
