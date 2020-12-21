package com.qonversion.android.sdk.di.module

import android.app.Application
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.logger.Logger
import dagger.Module
import dagger.Provides
import javax.inject.Named

@Module
class AppModule(
    private val application: Application,
    private val projectKey: String
) {
    @ApplicationScope
    @Provides
    fun provideApplication(): Application {
        return application
    }

    @ApplicationScope
    @Provides
    fun provideLogger(): Logger {
        return ConsoleLogger()
    }

    @ApplicationScope
    @Provides
    fun provideConfig(): QonversionConfig {
        return QonversionConfig(projectKey, SDK_VERSION, true)
    }

    @ApplicationScope
    @Provides
    @Named("projectKey")
    fun provideProjectKey(): String {
        return projectKey
    }

    companion object {
        private const val SDK_VERSION = "2.0.2"
    }
}
