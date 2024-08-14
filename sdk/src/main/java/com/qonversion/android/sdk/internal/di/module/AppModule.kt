package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides

@Module
internal class AppModule(
    private val application: Application,
    private val internalConfig: InternalConfig,
    private val appStateProvider: AppStateProvider
) {
    @ApplicationScope
    @Provides
    fun provideApplication(): Application {
        return application
    }

    @ApplicationScope
    @Provides
    fun provideConfig(): InternalConfig {
        return internalConfig
    }

    @ApplicationScope
    @Provides
    fun provideAppStateProvider(): AppStateProvider {
        return appStateProvider
    }

    @ApplicationScope
    @Provides
    fun provideSharedPreferences(context: Application): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @ApplicationScope
    @Provides
    fun provideSharedPreferencesCache(
        sharedPreferences: SharedPreferences
    ): SharedPreferencesCache {
        return SharedPreferencesCache(sharedPreferences)
    }

    @ApplicationScope
    @Provides
    fun provideLogger(): Logger {
        return ConsoleLogger()
    }

    @ApplicationScope
    @Provides
    fun providePurchasesCache(sharedPreferences: SharedPreferencesCache): PurchasesCache {
        return PurchasesCache(sharedPreferences)
    }

    @ApplicationScope
    @Provides
    fun provideLaunchResultCacheWrapper(
        moshi: Moshi,
        sharedPreferencesCache: SharedPreferencesCache,
        fallbacksService: QFallbacksService
    ): LaunchResultCacheWrapper {
        return LaunchResultCacheWrapper(moshi, sharedPreferencesCache, internalConfig, fallbacksService)
    }

    @ApplicationScope
    @Provides
    fun provideFallbackService(
        context: Application,
        moshi: Moshi,
        logger: Logger
    ): QFallbacksService {
        return QFallbacksService(context, internalConfig, moshi, logger)
    }
}
