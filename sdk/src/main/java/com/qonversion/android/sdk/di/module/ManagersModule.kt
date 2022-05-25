package com.qonversion.android.sdk.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.IncrementalDelayCalculator
import com.qonversion.android.sdk.QIdentityManager
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.services.QUserInfoService
import com.qonversion.android.sdk.QUserPropertiesManager
import com.qonversion.android.sdk.automations.AutomationsEventMapper
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.UserPropertiesStorage

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
        appContext: Application
    ): QAutomationsManager {
        return QAutomationsManager(repository, preferences, eventMapper, appContext)
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
        logger: Logger
    ): QUserPropertiesManager {
        return QUserPropertiesManager(appContext, repository, propertiesStorage, incrementalDelayCalculator, logger)
    }

    @ApplicationScope
    @Provides
    fun provideIncrementalDelayCalculator(): IncrementalDelayCalculator {
        return IncrementalDelayCalculator(Random())
    }
}
