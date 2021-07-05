package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionShowScreenCallback
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
                object {}.javaClass.enclosingMethod?.name
            )
    }

    /**
     * Show the screen using its ID.
     * @param withID - screen's ID that must be shown
     * @param callback - callback that is called when the screen is shown to a user
     */
    @JvmStatic
    fun showScreen(withID: String, callback: QonversionShowScreenCallback) {
        automationsManager?.loadScreen(withID, callback)
            ?: Qonversion.logLaunchErrorForFunctionName(
                object {}.javaClass.enclosingMethod?.name
            )
    }
}