package com.qonversion.android.sdk.internal.appState

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

internal class AppLifecycleObserverImpl : AppLifecycleObserver,
    LifecycleObserver {

    @VisibleForTesting
    val appStateChangeListeners = mutableSetOf<AppStateChangeListener>()

    @VisibleForTesting
    var appState = AppState.Background

    @VisibleForTesting
    var wasFirstForegroundFired = false

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onSwitchToForeground() {
        appState = AppState.Foreground

        fireToListeners { listener -> listener.onAppForeground() }
        if (!wasFirstForegroundFired) {
            wasFirstForegroundFired = true
            fireToListeners { listener -> listener.onAppFirstForeground() }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onSwitchToBackground() {
        appState = AppState.Background

        fireToListeners { listener -> listener.onAppBackground() }
    }

    override fun isInBackground(): Boolean {
        return appState == AppState.Background
    }

    override fun subscribeOnAppStateChanges(listener: AppStateChangeListener) {
        synchronized(appStateChangeListeners) {
            appStateChangeListeners.add(listener)
        }
    }

    override fun unsubscribeFromAppStateChanges(listener: AppStateChangeListener) {
        synchronized(appStateChangeListeners) {
            appStateChangeListeners.remove(listener)
        }
    }

    @VisibleForTesting
    fun fireToListeners(block: (listener: AppStateChangeListener) -> Unit) {
        appStateChangeListeners.forEach(block)
    }
}
