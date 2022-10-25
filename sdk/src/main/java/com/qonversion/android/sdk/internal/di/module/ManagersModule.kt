package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.IncrementalDelayCalculator
import com.qonversion.android.sdk.internal.QIdentityManager
import com.qonversion.android.sdk.internal.QonversionRepository
import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.QUserPropertiesManager
import com.qonversion.android.sdk.automations.AutomationsEventMapper
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.storage.UserPropertiesStorage

import dagger.Module
import dagger.Provides
import java.util.*

@Module
internal class ManagersModule {

    @ApplicationScope
    @Provides
    fun provideAutomationsManager(
        repository: QonversionRepository,
        preferences: SharedPreferences,
        eventMapper: AutomationsEventMapper,
        appContext: Application,
        appStateProvider: AppStateProvider
    ): QAutomationsManager {
        return QAutomationsManager(
            repository,
            preferences,
            eventMapper,
            appContext,
            appStateProvider
        )
    }

    @ApplicationScope
    @Provides
    fun provideAutomationsEventMapper(
        logger: Logger
    ): AutomationsEventMapper {
        return AutomationsEventMapper(logger)
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
        incrementalDelayCalculator: IncrementalDelayCalculator,
        appStateProvider: AppStateProvider,
        logger: Logger
    ): QUserPropertiesManager {
        return QUserPropertiesManager(
            appContext,
            repository,
            propertiesStorage,
            incrementalDelayCalculator,
            appStateProvider,
            logger
        )
    }

    @ApplicationScope
    @Provides
    fun provideIncrementalDelayCalculator(): IncrementalDelayCalculator {
        return IncrementalDelayCalculator(Random())
    }
}
