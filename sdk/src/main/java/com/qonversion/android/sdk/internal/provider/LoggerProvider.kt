package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.dto.LogLevel

internal interface LoggerProvider {

    val logLevel: LogLevel

    val logTag: String
}
