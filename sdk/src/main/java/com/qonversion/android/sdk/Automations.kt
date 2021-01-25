package com.qonversion.android.sdk

import com.qonversion.android.sdk.di.QDependencyInjector
import com.qonversion.android.sdk.push.QAutomationsDelegate
import com.qonversion.android.sdk.push.QAutomationsManager

object Automations {

    private val automationsManager: QAutomationsManager = QDependencyInjector.appComponent.automationsManager()

    /**
     * The delegate is responsible for handling in-app screens and actions when push notification is received.
     * Make sure the method is called before handlePushIfPossible
     */
    @JvmStatic
    fun setDelegate(delegate: QAutomationsDelegate) {
        automationsManager.automationsDelegate = delegate
    }
}