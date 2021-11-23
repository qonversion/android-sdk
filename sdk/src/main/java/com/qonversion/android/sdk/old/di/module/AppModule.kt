package com.qonversion.android.sdk.old.di.module

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.qonversion.android.sdk.old.QonversionConfig
import com.qonversion.android.sdk.old.di.scope.ApplicationScope
import com.qonversion.android.sdk.old.logger.ConsoleLogger
import com.qonversion.android.sdk.old.logger.Logger
import com.qonversion.android.sdk.old.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.old.storage.PurchasesCache
import com.qonversion.android.sdk.old.storage.SharedPreferencesCache
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides

@Module
class AppModule(
    private val application: Application,
    private val projectKey: String,
    private val isDebugMode: Boolean
) {
    @ApplicationScope
    @Provides
    fun provideApplication(): Application {
        return application
    }

    @ApplicationScope
    @Provides
    fun provideConfig(): QonversionConfig {
        return QonversionConfig(projectKey, SDK_VERSION, isDebugMode)
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
        private const val SDK_VERSION = "3.2.1"
    }
}
