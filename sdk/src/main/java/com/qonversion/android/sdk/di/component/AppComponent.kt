package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.QIdentityManager

import com.qonversion.android.sdk.QHandledPurchasesCache
import com.qonversion.android.sdk.QUserPropertiesManager
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.di.module.AppModule
import com.qonversion.android.sdk.di.module.RepositoryModule
import com.qonversion.android.sdk.di.module.NetworkModule
import com.qonversion.android.sdk.di.module.ManagersModule
import com.qonversion.android.sdk.di.module.ServicesModule
import com.qonversion.android.sdk.services.QUserInfoService
import com.qonversion.android.sdk.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.storage.PurchasesCache
import dagger.Component

@ApplicationScope
@Component(modules = [
    AppModule::class,
    RepositoryModule::class,
    NetworkModule::class,
    ManagersModule::class,
    ServicesModule::class
])
internal interface AppComponent {
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
