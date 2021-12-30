package com.qonversion.android.sdk.internal.logger

internal interface Logger {

    fun verbose(message: String)

    fun info(message: String)

    fun warn(message: String)

    fun error(message: String, throwable: Throwable? = null)
}
