package com.qonversion.android.sdk.internal.appState

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal class AppLifecycleObserver : Application.ActivityLifecycleCallbacks {
    var appState = AppState.Background
        private set

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        appState = AppState.Foreground
    }

    override fun onActivityPaused(activity: Activity) {
        appState = AppState.Background
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    fun isBackground(): Boolean {
        return appState == AppState.Background
    }
}
