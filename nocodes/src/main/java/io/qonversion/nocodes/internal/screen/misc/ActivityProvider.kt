package io.qonversion.nocodes.internal.screen.misc

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

internal class ActivityProvider(application: Application) : Application.ActivityLifecycleCallbacks {

    private var currentActivity: WeakReference<Activity>? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun getCurrentActivity(): Activity? = currentActivity?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
