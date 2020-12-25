package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.QonversionRepository

import com.qonversion.android.sdk.push.QAutomationManager
import com.qonversion.android.sdk.di.module.AutomationModule
import com.qonversion.android.sdk.di.scope.AutomationScope
import com.qonversion.android.sdk.logger.Logger
import dagger.Component

@AutomationScope
@Component(dependencies = [AppComponent::class], modules = [AutomationModule::class])
interface AutomationComponent {
    fun repository(): QonversionRepository
    fun logger(): Logger
    fun automationManager(): QAutomationManager
}