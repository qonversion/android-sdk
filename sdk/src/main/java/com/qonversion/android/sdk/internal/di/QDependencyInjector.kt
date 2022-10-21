package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.di.component.AppComponent
import com.qonversion.android.sdk.internal.di.component.DaggerAppComponent
import com.qonversion.android.sdk.internal.di.module.AppModule
import com.qonversion.android.sdk.internal.di.module.ManagersModule
import com.qonversion.android.sdk.internal.di.module.RepositoryModule

internal object QDependencyInjector {
    internal lateinit var appComponent: AppComponent
        private set

    internal fun buildAppComponent(
        context: Application,
        projectKey: String,
        isDebugMode: Boolean,
        isObserveMode: Boolean
    ): AppComponent {
        appComponent = DaggerAppComponent
            .builder()
            .appModule(AppModule(context, projectKey, isDebugMode, isObserveMode))
            .repositoryModule(RepositoryModule())
            .managersModule(ManagersModule())
            .build()

        return appComponent
    }

    fun isAppComponentInitialized() = ::appComponent.isInitialized
}
