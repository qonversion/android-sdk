package com.qonversion.android.sdk.push

import android.app.Activity

/**
 * The delegate is responsible for handling in-app screens and actions when push notification is received.
 * Make sure the method is called before handlePushIfPossible
 */
interface QAutomationsDelegate {
    /**
     * Provide the current Activity context
     */
    fun activityForScreen(): Activity

    /**
     * Returns the final action that the user completed on the in-app screen.
     * @param action the final action on the in-app screen. For instance,
     * if the user makes purchase then
     * action = QAction(type=Purchase, value={value=in_app})
     */
    fun automationsFinishedWithAction(action: QAction)
}