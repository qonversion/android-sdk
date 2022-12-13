package com.qonversion.android.sdk.automations.internal

import com.qonversion.android.sdk.automations.Automations
import com.qonversion.android.sdk.automations.AutomationsDelegate
import com.qonversion.android.sdk.listeners.QonversionShowScreenCallback
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import java.lang.ref.WeakReference

internal class AutomationsInternal : Automations {
    private val automationsManager: QAutomationsManager =
        QDependencyInjector.appComponent.automationsManager()

    override fun setDelegate(delegate: AutomationsDelegate) {
        automationsManager.automationsDelegate = WeakReference(delegate)
    }

    override fun showScreen(withID: String, callback: QonversionShowScreenCallback) {
        automationsManager.loadScreen(withID, callback)
    }

    override fun setNotificationsToken(token: String) {
        automationsManager.setPushToken(token)
    }

    override fun handleNotification(messageData: Map<String, String>): Boolean {
        return automationsManager.handlePushIfPossible(messageData)
    }

    override fun getNotificationCustomPayload(messageData: Map<String, String>): Map<String, Any?>? {
        return automationsManager.getNotificationCustomPayload(messageData)
    }
}
