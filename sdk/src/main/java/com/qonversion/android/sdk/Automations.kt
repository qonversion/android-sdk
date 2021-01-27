package com.qonversion.android.sdk

import com.qonversion.android.sdk.di.QDependencyInjector
import com.qonversion.android.sdk.push.QAutomationsDelegate
import com.qonversion.android.sdk.push.QAutomationsManager
import java.lang.ref.WeakReference

object Automations {

    private val automationsManager: QAutomationsManager? =
        if (QDependencyInjector.isAppComponentInitialized()) {
            QDependencyInjector.appComponent.automationsManager()
        } else {
            null
        }

    /**
     * The delegate is responsible for handling in-app screens and actions when push notification is received.
     * Make sure the method is called before handleNotification
     */
    @JvmStatic
    fun setDelegate(delegate: QAutomationsDelegate) {
        if (automationsManager == null) {
            Qonversion.logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
            return
        }
        automationsManager.automationsDelegate = WeakReference(delegate)
    }
}