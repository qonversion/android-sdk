package com.qonversion.android.sdk.di.module

import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.screens.QAutomationDelegate
import com.qonversion.android.sdk.screens.QScreenManager
import com.qonversion.android.sdk.di.scope.ActionScope
import dagger.Module
import dagger.Provides

@Module
class ScreenModule(private val automationDelegate: QAutomationDelegate) {

    @ActionScope
    @Provides
    fun provideScreenManager(repository:QonversionRepository): QScreenManager {
        return QScreenManager(automationDelegate, repository)
    }
}
