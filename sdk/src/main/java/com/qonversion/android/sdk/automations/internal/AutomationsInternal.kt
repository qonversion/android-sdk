package com.qonversion.android.sdk.automations.internal

import com.qonversion.android.sdk.automations.Automations
import com.qonversion.android.sdk.automations.AutomationsDelegate
import com.qonversion.android.sdk.listeners.QonversionShowScreenCallback
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import java.lang.ref.WeakReference

internal class AutomationsInternal : Automations {
    private var logger = ConsoleLogger()
    private val automationsManager: QAutomationsManager? =
        if (QDependencyInjector.isAppComponentInitialized()) {
            QDependencyInjector.appComponent.automationsManager()
        } else {
            null
        }

    override fun setDelegate(delegate: AutomationsDelegate) {
        automationsManager?.let { it.automationsDelegate = WeakReference(delegate) }
            ?: logLaunchErrorForFunctionName(
                object {}.javaClass.enclosingMethod?.name
            )
    }

    override fun showScreen(withID: String, callback: QonversionShowScreenCallback) {
        automationsManager?.loadScreen(withID, callback)
            ?: logLaunchErrorForFunctionName(
                object {}.javaClass.enclosingMethod?.name
            )
    }

    override fun setNotificationsToken(token: String) {
        automationsManager?.setPushToken(token)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun handleNotification(messageData: Map<String, String>): Boolean {
        return automationsManager?.handlePushIfPossible(messageData) ?: run {
            logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
            return@run false
        }
    }

    override fun getNotificationCustomPayload(messageData: Map<String, String>): Map<String, Any?>? {
        return automationsManager?.getNotificationCustomPayload(messageData)
    }

    private fun logLaunchErrorForFunctionName(functionName: String?) {
        logger.release("$functionName function can not be executed. It looks like launch was not called.")
    }
}
