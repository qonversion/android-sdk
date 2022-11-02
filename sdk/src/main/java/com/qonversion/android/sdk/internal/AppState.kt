package com.qonversion.android.sdk.internal

internal enum class AppState {
    Foreground,
    Background;

    fun isBackground() =
        this == Background
}
