package com.qonversion.android.sdk.internal.networkLayer

internal enum class ApiHeader(val value: String) {
    ContentType("Content-Type"),
    Authorization("Authorization"),
    Locale("User-Locale"),
    Source("Source"),
    SourceVersion("Source-Version"),
    Platform("Platform"),
    PlatformVersion("Platform-Version"),
    UserID("User-Id")
}
