package com.qonversion.android.sdk.internal.appState

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.lang.ref.WeakReference

internal class AppLifecycleObserverImpl : AppLifecycleObserver,
    LifecycleObserver {

    @VisibleForTesting
    val appStateChangeListeners = mutableSetOf<WeakReference<AppStateChangeListener>>()

    @VisibleForTesting
    var appState = AppState.Background

    @VisibleForTesting
    var isFirstForegroundPassed = false

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onSwitchToForeground() {
        appState = AppState.Foreground

        fireToListeners { listener -> listener.onAppForeground(!isFirstForegroundPassed) }
        isFirstForegroundPassed = true
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
            appStateChangeListeners.add(WeakReference(listener))
        }
    }

    override fun unsubscribeFromAppStateChanges(listener: AppStateChangeListener) {
        synchronized(appStateChangeListeners) {
            appStateChangeListeners.removeAll { it.get() === listener }
        }
    }

    @VisibleForTesting
    fun fireToListeners(block: (listener: AppStateChangeListener) -> Unit) {
        appStateChangeListeners
            .mapNotNull { it.get() }
            .forEach(block)
    }
}
