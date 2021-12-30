package com.qonversion.android.sdk.dto

enum class LogLevel(val level: Int) {
    Verbose(0),
    Info(10),
    Warning(20),
    Error(30),
    Disabled(Int.MAX_VALUE)
}
