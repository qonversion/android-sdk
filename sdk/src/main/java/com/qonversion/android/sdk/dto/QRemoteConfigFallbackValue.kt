package com.qonversion.android.sdk.dto

/**
 * A value read directly from the Remote Config defaults bundled with the app.
 *
 * [rawValue] is one of the JSON-compatible Kotlin values: [String], [Double],
 * [Boolean], an immutable [List], an immutable [Map], or `null`. The wrapper
 * itself remains non-null for a present JSON `null`, so callers can distinguish
 * that value from a missing or invalid bundled key.
 */
class QRemoteConfigFallbackValue internal constructor(
    val rawValue: Any?,
)
