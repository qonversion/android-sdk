package com.qonversion.android.sdk.internal.appState

internal interface AppLifecycleObserver {

    fun isInBackground(): Boolean

    fun subscribeOnAppStateChanges(listener: AppStateChangeListener)

    fun unsubscribeFromAppStateChanges(listener: AppStateChangeListener)
}
