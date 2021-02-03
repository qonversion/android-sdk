package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.di.module.AppModule
import com.qonversion.android.sdk.di.module.ManagersModule
import com.qonversion.android.sdk.di.module.NetworkModule
import com.qonversion.android.sdk.di.module.RepositoryModule
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.storage.DeviceStorage
import dagger.Component

@ApplicationScope
@Component(modules = [AppModule::class, RepositoryModule::class, NetworkModule::class, ManagersModule::class])
interface AppComponent {
    fun repository(): QonversionRepository
    fun deviceStorage(): DeviceStorage
    fun automationsManager(): QAutomationsManager
}