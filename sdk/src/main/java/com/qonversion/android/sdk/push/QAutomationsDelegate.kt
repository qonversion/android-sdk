package com.qonversion.android.sdk.push

import android.content.Context

/**
 * The delegate is responsible for handling in-app screens and actions when push notification is received.
 * Make sure the method is called before handleNotification
 */
interface QAutomationsDelegate {
    /**
     * Provide the context for screen intent
     */
    fun contextForScreenIntent(): Context


    /**
     * Returns the final action that the user completed on the in-app screen.
     * @param action the final action on the in-app screen. For instance,
     * if the user makes a purchase then action.type = QActionResultType.Purchase.
     * You can use the Qonversion.checkPermissions() method to get available permissions
     */
    fun automationFinishedWithAction(action: QActionResult)
}