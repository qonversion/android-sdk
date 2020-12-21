package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.di.module.AppModule
import com.qonversion.android.sdk.di.module.NetworkModule
import com.qonversion.android.sdk.di.module.RepositoryModule
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.logger.Logger
import dagger.Component
import javax.inject.Named

@ApplicationScope
@Component(modules = [AppModule::class, RepositoryModule::class, NetworkModule::class])
interface AppComponent {
    fun repository(): QonversionRepository
    fun logger(): Logger

    @Named("projectKey")
    fun key(): String
}