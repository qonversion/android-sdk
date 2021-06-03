package com.qonversion.android.sdk.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.IncrementalCounter
import com.qonversion.android.sdk.QIdentityManager
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.services.QUserInfoService
import com.qonversion.android.sdk.QUserPropertiesManager
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.UserPropertiesStorage

import dagger.Module
import dagger.Provides
import java.util.*

@Module
class ManagersModule {

    @ApplicationScope
    @Provides
    fun provideAutomationsManager(
        repository: QonversionRepository,
        preferences: SharedPreferences,
        appContext: Application
    ): QAutomationsManager {
        return QAutomationsManager(repository, preferences, appContext)
    }

    @ApplicationScope
    @Provides
    fun provideIdentityManager(
        repository: QonversionRepository,
        userInfoService: QUserInfoService
    ): QIdentityManager {
        return QIdentityManager(repository, userInfoService)
    }

    @ApplicationScope
    @Provides
    fun provideUserPropertiesManager(
        appContext: Application,
        repository: QonversionRepository,
        propertiesStorage: UserPropertiesStorage,
        incrementalCounter: IncrementalCounter,
        logger: Logger
    ): QUserPropertiesManager {
        return QUserPropertiesManager(appContext, repository, propertiesStorage, incrementalCounter, logger)
    }

    @ApplicationScope
    @Provides
    fun provideIncrementalCounter(): IncrementalCounter {
        return IncrementalCounter(Random())
    }
}
