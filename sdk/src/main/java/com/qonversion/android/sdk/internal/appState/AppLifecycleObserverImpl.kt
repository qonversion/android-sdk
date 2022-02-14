package com.qonversion.android.sdk.internal.appState

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal class AppLifecycleObserverImpl : AppLifecycleObserver,
    Application.ActivityLifecycleCallbacks {
    var appState = AppState.Background

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        appState = AppState.Foreground
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        appState = AppState.Background
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun isInBackground(): Boolean {
        return appState == AppState.Background
    }

    override fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }
}
