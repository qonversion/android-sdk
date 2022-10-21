package com.qonversion.android.sdk.internal.di.module

import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.qonversion.android.sdk.internal.storage.TokenStorage
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
