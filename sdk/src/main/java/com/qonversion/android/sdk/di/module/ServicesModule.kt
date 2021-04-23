package com.qonversion.android.sdk.di.module

import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.services.QUserInfoService
import com.qonversion.android.sdk.storage.SharedPreferencesCache
import com.qonversion.android.sdk.storage.TokenStorage
import dagger.Module
import dagger.Provides

@Module
class ServicesModule {

    @ApplicationScope
    @Provides
    fun provideUserInfoService(
        cacheStorage: SharedPreferencesCache,
        tokenStorage: TokenStorage
    ): QUserInfoService {
        return QUserInfoService(cacheStorage, tokenStorage)
    }

}