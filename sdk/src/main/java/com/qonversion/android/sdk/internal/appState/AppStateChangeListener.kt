package com.qonversion.android.sdk.internal.appState

interface AppStateChangeListener {

    fun onAppFirstForeground() = Unit

    fun onAppForeground() = Unit

    fun onAppBackground() = Unit
}