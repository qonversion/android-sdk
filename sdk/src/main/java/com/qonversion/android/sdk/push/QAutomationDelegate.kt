package com.qonversion.android.sdk.push

import android.app.Activity

interface QAutomationDelegate {
    fun provideActivityForScreen(): Activity
    fun automationFlowFinishedWithAction(action: QAction)
}