package com.qonversion.android.sdk.old.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.old.IncrementalDelayCalculator
import com.qonversion.android.sdk.old.QIdentityManager
import com.qonversion.android.sdk.old.QonversionRepository
import com.qonversion.android.sdk.old.di.scope.ApplicationScope
import com.qonversion.android.sdk.old.automations.QAutomationsManager
import com.qonversion.android.sdk.old.services.QUserInfoService
import com.qonversion.android.sdk.old.QUserPropertiesManager
import com.qonversion.android.sdk.old.automations.AutomationsEventMapper
import com.qonversion.android.sdk.old.logger.Logger
import com.qonversion.android.sdk.old.storage.UserPropertiesStorage

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
