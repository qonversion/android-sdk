package com.qonversion.android.sdk.di

import android.app.Application
import com.qonversion.android.sdk.di.component.AppComponent
import com.qonversion.android.sdk.di.component.DaggerAppComponent
import com.qonversion.android.sdk.di.module.AppModule
import com.qonversion.android.sdk.di.module.ManagersModule
import com.qonversion.android.sdk.di.module.RepositoryModule

object QDependencyInjector {
    internal lateinit var appComponent: AppComponent
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
            .managersModule(ManagersModule())
            .build()

        return appComponent
    }
}