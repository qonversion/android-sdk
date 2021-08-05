package com.qonversion.android.sdk

enum class AppState {
    Foreground,
    Background,
    PendingBackground,
    PendingForeground;

    fun isBackground() =
        this == Background
}