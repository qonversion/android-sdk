package com.qonversion.android.sdk.internal.di.component

import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.internal.QAutomationsManager
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.QHandledPurchasesCache
import com.qonversion.android.sdk.internal.QIdentityManager
import com.qonversion.android.sdk.internal.QRemoteConfigManager
import com.qonversion.android.sdk.internal.QUserPropertiesManager
import com.qonversion.android.sdk.internal.di.module.AppModule
import com.qonversion.android.sdk.internal.di.module.RepositoryModule
import com.qonversion.android.sdk.internal.di.module.NetworkModule
import com.qonversion.android.sdk.internal.di.module.ManagersModule
import com.qonversion.android.sdk.internal.di.module.ServicesModule
import com.qonversion.android.sdk.internal.logger.QExceptionManager
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.repository.DefaultRepository
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
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
    fun repository(): QRepository
    fun qonversionRepository(): DefaultRepository
    fun purchasesCache(): PurchasesCache
    fun handledPurchasesCache(): QHandledPurchasesCache
    fun launchResultCacheWrapper(): LaunchResultCacheWrapper
    fun automationsManager(): QAutomationsManager
    fun identityManager(): QIdentityManager
    fun userInfoService(): QUserInfoService
    fun userPropertiesManager(): QUserPropertiesManager
    fun remoteConfigManager(): QRemoteConfigManager
    fun internalConfig(): InternalConfig
    fun appStateProvider(): AppStateProvider
    fun sharedPreferencesCache(): SharedPreferencesCache
    fun exceptionManager(): QExceptionManager
    fun fallbacksService(): QFallbacksService
}
