package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.qonversion.android.sdk.internal.QonversionConfig
import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides

@Module
internal class AppModule(
    private val application: Application,
    private val projectKey: String,
    private val isDebugMode: Boolean,
    private val isObserveMode: Boolean
) {
    @ApplicationScope
    @Provides
    fun provideApplication(): Application {
        return application
    }

    @ApplicationScope
    @Provides
    fun provideConfig(): QonversionConfig {
        return QonversionConfig(projectKey, SDK_VERSION, isDebugMode, isObserveMode)
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
    fun providePurchasesCache(sharedPreferences: SharedPreferences): PurchasesCache {
        return PurchasesCache(sharedPreferences)
    }

    @ApplicationScope
    @Provides
    fun provideLaunchResultCacheWrapper(
        moshi: Moshi,
        sharedPreferencesCache: SharedPreferencesCache
    ): LaunchResultCacheWrapper {
        return LaunchResultCacheWrapper(moshi, sharedPreferencesCache)
    }

    companion object {
        private const val SDK_VERSION = "3.4.0"
    }
}
