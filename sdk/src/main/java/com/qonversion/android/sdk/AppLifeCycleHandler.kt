package com.qonversion.android.sdk

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

internal class AppLifecycleHandler(private val lifecycleDelegate: LifecycleDelegate) :
    LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onMoveToBackground() {
        lifecycleDelegate.onAppBackgrounded()
    }
}

internal interface LifecycleDelegate {
    fun onAppBackgrounded()
}