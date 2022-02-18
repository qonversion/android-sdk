package com.qonversion.android.sdk.internal.appState

interface AppStateChangeListener {

    fun onAppForeground(isFirst: Boolean) = Unit

    fun onAppBackground() = Unit
}
