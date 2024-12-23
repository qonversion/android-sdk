package io.qonversion.nocodes.dto

import io.qonversion.nocodes.NoCodes

/**
 * This enum contains available settings for LogLevel.
 * Provide it to the configuration object via [NoCodes.initialize] while initializing the SDK
 * or via [NoCodes.setLogLevel] after initializing the SDK.
 * The default value SDK uses is [LogLevel.Info].
 *
 * You could change the log level to make logs from the Qonversion No-Codes SDK more detailed or strict.
 */
enum class LogLevel(val level: Int) {
    // All available logs (function started, function finished, data fetched, etc)
    Verbose(0),

    // Info level (data fetched, products loaded, screen fetched, etc)
    Info(10),

    // Warning level (data fetched partially, etc)
    Warning(20),

    // Error level (data fetch failed, activity failed to start, etc)
    Error(30),

    // Logging is disabled at all
    Disabled(Int.MAX_VALUE)
}