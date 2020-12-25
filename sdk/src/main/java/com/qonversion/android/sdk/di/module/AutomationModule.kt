package com.qonversion.android.sdk.di.module

import android.content.SharedPreferences
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.push.QAutomationDelegate
import com.qonversion.android.sdk.push.QAutomationManager
import com.qonversion.android.sdk.di.scope.AutomationScope
import dagger.Module
import dagger.Provides

@Module
class AutomationModule(private val automationDelegate: QAutomationDelegate) {

    @AutomationScope
    @Provides
    fun provideScreenManager(repository:QonversionRepository, preferences: SharedPreferences): QAutomationManager {
        return QAutomationManager(automationDelegate, repository, preferences)
    }
}
