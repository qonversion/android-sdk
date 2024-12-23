package io.qonversion.nocodes.internal.provider

import io.qonversion.nocodes.dto.LogLevel

internal interface LoggerConfigProvider {

    val logLevel: LogLevel

    val logTag: String
}