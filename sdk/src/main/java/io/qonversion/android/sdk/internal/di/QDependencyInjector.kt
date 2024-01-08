package io.qonversion.android.sdk.internal.di

import android.app.Application
import io.qonversion.android.sdk.internal.InternalConfig
import io.qonversion.android.sdk.internal.di.component.AppComponent
import io.qonversion.android.sdk.internal.di.component.DaggerAppComponent
import io.qonversion.android.sdk.internal.di.module.AppModule
import io.qonversion.android.sdk.internal.di.module.ManagersModule
import io.qonversion.android.sdk.internal.di.module.RepositoryModule
import io.qonversion.android.sdk.internal.provider.AppStateProvider

internal object QDependencyInjector {
    internal lateinit var appComponent: AppComponent
        private set

    internal fun buildAppComponent(
        context: Application,
        internalConfig: InternalConfig,
        appStateProvider: AppStateProvider
    ): AppComponent {
        appComponent = DaggerAppComponent
            .builder()
            .appModule(AppModule(context, internalConfig, appStateProvider))
            .repositoryModule(RepositoryModule())
            .managersModule(ManagersModule())
            .build()

        return appComponent
    }
}
