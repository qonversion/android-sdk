package io.qonversion.android.sdk.internal.di.module

import io.qonversion.android.sdk.internal.di.scope.ApplicationScope
import io.qonversion.android.sdk.internal.services.QUserInfoService
import io.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import io.qonversion.android.sdk.internal.storage.TokenStorage
import dagger.Module
import dagger.Provides

@Module
internal class ServicesModule {

    @ApplicationScope
    @Provides
    fun provideUserInfoService(
        cacheStorage: SharedPreferencesCache,
        tokenStorage: TokenStorage
    ): QUserInfoService {
        return QUserInfoService(cacheStorage, tokenStorage)
    }
}
