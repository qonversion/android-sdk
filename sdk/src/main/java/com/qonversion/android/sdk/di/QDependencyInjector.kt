package com.qonversion.android.sdk.di

import android.app.Application
import com.qonversion.android.sdk.di.component.*

import com.qonversion.android.sdk.screens.QAutomationDelegate
import com.qonversion.android.sdk.di.module.ScreenModule
import com.qonversion.android.sdk.di.module.AppModule
import com.qonversion.android.sdk.di.module.RepositoryModule

object QDependencyInjector {
    internal lateinit var appComponent: AppComponent
        private set

    internal lateinit var screenComponent: ScreenComponent
        private set

    fun buildAppComponent(
        context: Application,
        projectKey: String
    ): AppComponent {
        appComponent = DaggerAppComponent
            .builder()
            .appModule(AppModule(context, projectKey))
            .repositoryModule(RepositoryModule())
            .build()

        return appComponent
    }

    fun buildActionComponent(
        automationDelegate: QAutomationDelegate
    ): ScreenComponent {
        screenComponent = DaggerScreenComponent.builder()
            .appComponent(appComponent)
            .screenModule(ScreenModule(automationDelegate))
            .build()

        return screenComponent
    }
}