package io.qonversion.android.sdk.internal.di.component

import io.qonversion.android.sdk.internal.di.scope.ApplicationScope
import io.qonversion.android.sdk.automations.internal.QAutomationsManager
import io.qonversion.android.sdk.internal.InternalConfig
import io.qonversion.android.sdk.internal.QHandledPurchasesCache
import io.qonversion.android.sdk.internal.QIdentityManager
import io.qonversion.android.sdk.internal.QRemoteConfigManager
import io.qonversion.android.sdk.internal.QUserPropertiesManager
import io.qonversion.android.sdk.internal.di.module.AppModule
import io.qonversion.android.sdk.internal.di.module.RepositoryModule
import io.qonversion.android.sdk.internal.di.module.NetworkModule
import io.qonversion.android.sdk.internal.di.module.ManagersModule
import io.qonversion.android.sdk.internal.di.module.ServicesModule
import io.qonversion.android.sdk.internal.logger.QExceptionManager
import io.qonversion.android.sdk.internal.provider.AppStateProvider
import io.qonversion.android.sdk.internal.repository.QRepository
import io.qonversion.android.sdk.internal.repository.DefaultRepository
import io.qonversion.android.sdk.internal.services.QUserInfoService
import io.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import io.qonversion.android.sdk.internal.storage.PurchasesCache
import io.qonversion.android.sdk.internal.storage.SharedPreferencesCache
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
}
