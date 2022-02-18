package com.qonversion.android.sdk.internal.appState

internal interface AppLifecycleObserver {

    fun isInBackground(): Boolean

    fun addListener(listener: AppStateChangeListener)

    fun removeListener(listener: AppStateChangeListener)
}
