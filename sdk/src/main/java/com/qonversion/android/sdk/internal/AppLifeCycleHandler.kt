package com.qonversion.android.sdk.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class AppLifecycleHandler(private val lifecycleDelegate: LifecycleDelegate) :
    DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        lifecycleDelegate.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        lifecycleDelegate.onAppBackground()
    }
}

internal interface LifecycleDelegate {
    fun onAppBackground()
    fun onAppForeground()
}
