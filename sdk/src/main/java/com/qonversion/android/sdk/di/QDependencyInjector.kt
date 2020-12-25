package com.qonversion.android.sdk.di

import android.app.Application
import com.qonversion.android.sdk.di.component.*

import com.qonversion.android.sdk.push.QAutomationDelegate
import com.qonversion.android.sdk.di.module.AutomationModule
import com.qonversion.android.sdk.di.module.AppModule
import com.qonversion.android.sdk.di.module.RepositoryModule

object QDependencyInjector {
    internal lateinit var appComponent: AppComponent
        private set

    internal lateinit var automationComponent: AutomationComponent
        private set

    fun buildAppComponent(
        context: Application,
        projectKey: String,
        isDebugMode: Boolean
    ): AppComponent {
        appComponent = DaggerAppComponent
            .builder()
            .appModule(AppModule(context, projectKey, isDebugMode))
            .repositoryModule(RepositoryModule())
            .build()

        return appComponent
    }

    fun buildAutomationComponent(
        automationDelegate: QAutomationDelegate
    ): AutomationComponent {
        automationComponent = DaggerAutomationComponent.builder()
            .appComponent(appComponent)
            .automationModule(AutomationModule(automationDelegate))
            .build()

        return automationComponent
    }
}