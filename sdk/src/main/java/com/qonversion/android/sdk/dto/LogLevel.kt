package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion

/**
 * This enum contains an available settings for LogLevel.
 * Provide it to the configuration object via [Qonversion.initialize] while initializing the SDK
 * or via [Qonversion.setLogLevel] after initializing the SDK.
 * The default value is [LogLevel.Info].
 *
 * You could change the log level to make logs from Qonversion SDK more detailed or strict.
 */
enum class LogLevel(val level: Int) {
    // all available logs (function started, function finished, data fetched, etc)
    Verbose(0),

    // info level (data fetched, products loaded, user info fetched, etc)
    Info(10),

    // warning level (data fetched partially, sandbox env enabled for release build, etc)
    Warning(20),

    // error level (data fetch failed, Google billing is not available, etc)
    Error(30),

    // logging disabled at all
    Disabled(Int.MAX_VALUE)
}
