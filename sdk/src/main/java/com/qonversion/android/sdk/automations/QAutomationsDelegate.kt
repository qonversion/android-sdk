package com.qonversion.android.sdk.automations

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
     * Called when automations screen shown
     */
    fun automationsDidShowScreen(screenId: String)

    /**
     * Called when automations did start an action
     */
    fun automationsDidStartExecuting(actionResult: QActionResult)

    /**
     * Called when automations did fail an action
     */
    fun automationsDidFailExecuting(actionResult: QActionResult)

    /**
     * Returns the final action that the user completed on the in-app screen.
     * @param actionResult the final action on the in-app screen. For instance,
     * if the user makes a purchase then action.type = QActionResultType.Purchase.
     * You can use the Qonversion.checkPermissions() method to get available permissions
     */
    fun automationsDidFinishExecuting(actionResult: QActionResult)

    /**
     * This function called when automations finished the flow and closed the screen
     */
    fun automationsFinished()

}