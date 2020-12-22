package com.qonversion.android.sdk.screens

import android.app.Activity

interface QAutomationDelegate {
    fun provideActivityForScreen(): Activity
}