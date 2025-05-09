package io.qonversion.nocodes.internal.networkLayer

internal enum class ApiHeader(val key: String) {
    ContentType("Content-Type"),
    Authorization("Authorization"),
    Locale("User-Locale"),
    Source("Source"),
    SourceVersion("Source-Version"),
    Platform("Platform"),
    PlatformVersion("Platform-Version"),
    UserID("User-Id")
}
