package io.qonversion.nocodes.internal.logger

internal interface Logger {

    fun verbose(message: String)

    fun info(message: String, throwable: Throwable? = null)

    fun warn(message: String, throwable: Throwable? = null)

    fun error(message: String, throwable: Throwable? = null)
}