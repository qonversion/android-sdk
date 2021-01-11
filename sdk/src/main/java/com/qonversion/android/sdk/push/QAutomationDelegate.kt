package com.qonversion.android.sdk.push

import android.app.Activity

interface QAutomationDelegate {
    /**
     * Provide current activity for showing a screen
     */
    fun provideActivityForScreen(): Activity

    /**
     * Returns the action with which the automation flow was finished
     * @param action the last action in the automation flow
     */
    fun automationFlowFinishedWithAction(action: QAction)
}