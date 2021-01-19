package com.qonversion.android.sdk.di.module

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.logger.Logger
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
    fun provideLogger(): Logger {
        return ConsoleLogger()
    }

    companion object {
        private const val SDK_VERSION = "2.2.0"
    }
}
