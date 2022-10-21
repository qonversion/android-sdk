package com.qonversion.android.sdk.internal

internal enum class AppState {
    Foreground,
    Background,
    PendingBackground,
    PendingForeground;

    fun isBackground() =
        this == Background
}
