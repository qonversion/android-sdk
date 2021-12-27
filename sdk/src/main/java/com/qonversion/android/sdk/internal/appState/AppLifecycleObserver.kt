package com.qonversion.android.sdk.internal.appState

import android.app.Application

internal interface AppLifecycleObserver {

    fun isInBackground(): Boolean

    fun registerObserver(context: Application)
}
