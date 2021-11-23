package com.qonversion.android.sdk.old.di.component

import com.qonversion.android.sdk.old.QIdentityManager

import com.qonversion.android.sdk.old.QHandledPurchasesCache
import com.qonversion.android.sdk.old.QUserPropertiesManager
import com.qonversion.android.sdk.old.QonversionConfig
import com.qonversion.android.sdk.old.QonversionRepository
import com.qonversion.android.sdk.old.di.scope.ApplicationScope
import com.qonversion.android.sdk.old.automations.QAutomationsManager
import com.qonversion.android.sdk.old.di.module.AppModule
import com.qonversion.android.sdk.old.di.module.RepositoryModule
import com.qonversion.android.sdk.old.di.module.NetworkModule
import com.qonversion.android.sdk.old.di.module.ManagersModule
import com.qonversion.android.sdk.old.di.module.ServicesModule
import com.qonversion.android.sdk.old.services.QUserInfoService
import com.qonversion.android.sdk.old.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.old.storage.PurchasesCache
import dagger.Component

@ApplicationScope
@Component(modules = [
    AppModule::class,
    RepositoryModule::class,
    NetworkModule::class,
    ManagersModule::class,
    ServicesModule::class
])
interface AppComponent {
    fun repository(): QonversionRepository
    fun purchasesCache(): PurchasesCache
    fun handledPurchasesCache(): QHandledPurchasesCache
    fun launchResultCacheWrapper(): LaunchResultCacheWrapper
    fun automationsManager(): QAutomationsManager
    fun identityManager(): QIdentityManager
    fun userInfoService(): QUserInfoService
    fun userPropertiesManager(): QUserPropertiesManager
    fun qonversionConfig(): QonversionConfig
}
