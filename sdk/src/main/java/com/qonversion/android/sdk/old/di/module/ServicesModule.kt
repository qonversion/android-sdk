package com.qonversion.android.sdk.old.di.module

import com.qonversion.android.sdk.old.di.scope.ApplicationScope
import com.qonversion.android.sdk.old.services.QUserInfoService
import com.qonversion.android.sdk.old.storage.SharedPreferencesCache
import com.qonversion.android.sdk.old.storage.TokenStorage
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
