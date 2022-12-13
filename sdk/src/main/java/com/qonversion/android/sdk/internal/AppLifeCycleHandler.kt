package com.qonversion.android.sdk.internal

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

internal class AppLifecycleHandler(private val lifecycleDelegate: LifecycleDelegate) :
    LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        lifecycleDelegate.onAppForeground()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onMoveToBackground() {
        lifecycleDelegate.onAppBackground()
    }
}

internal interface LifecycleDelegate {
    fun onAppBackground()
    fun onAppForeground()
}
