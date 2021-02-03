package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.di.QDependencyInjector
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
     * Make sure the method is called before Qonversion.handleNotification
     */
    @JvmStatic
    fun setDelegate(delegate: AutomationsDelegate) {
        automationsManager?.let { it.automationsDelegate = WeakReference(delegate) }
            ?: Qonversion.logLaunchErrorForFunctionName(
                object {}.javaClass.enclosingMethod?.name)
    }
}