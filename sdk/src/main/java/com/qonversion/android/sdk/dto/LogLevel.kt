package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion

/**
 * This enum contains available settings for LogLevel.
 * Provide it to the configuration object via [Qonversion.initialize] while initializing the SDK
 * or via [Qonversion.setLogLevel] after initializing the SDK.
 * The default value SDK uses is [LogLevel.Info].
 *
 * You could change the log level to make logs from the Qonversion SDK more detailed or strict.
 */
enum class LogLevel(val level: Int) {
    // All available logs (function started, function finished, data fetched, etc)
    Verbose(0),

    // Info level (data fetched, products loaded, user info fetched, etc)
    Info(10),

    // Warning level (data fetched partially, sandbox env enabled for release build, etc)
    Warning(20),

    // Error level (data fetch failed, Google billing is not available, etc)
    Error(30),

    // Logging is disabled at all
    Disabled(Int.MAX_VALUE)
}
