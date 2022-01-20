package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.dto.LogLevel

internal interface LoggerConfigsProvider {

    val logLevel: LogLevel

    val logTag: String
}
