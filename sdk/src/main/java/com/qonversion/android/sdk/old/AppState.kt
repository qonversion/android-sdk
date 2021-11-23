package com.qonversion.android.sdk.old

enum class AppState {
    Foreground,
    Background,
    PendingBackground,
    PendingForeground;

    fun isBackground() =
        this == Background
}
