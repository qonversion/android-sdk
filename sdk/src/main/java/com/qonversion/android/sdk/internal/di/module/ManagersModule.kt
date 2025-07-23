package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import com.qonversion.android.sdk.internal.IncrementalDelayCalculator
import com.qonversion.android.sdk.internal.QIdentityManager
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.QUserPropertiesManager
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.storage.UserPropertiesStorage

import dagger.Module
import dagger.Provides
import java.util.Random

@Module
internal class ManagersModule {

    @ApplicationScope
    @Provides
    fun provideIdentityManager(
        repository: QRepository,
        userInfoService: QUserInfoService
    ): QIdentityManager {
        return QIdentityManager(repository, userInfoService)
    }

    @ApplicationScope
    @Provides
    fun provideUserPropertiesManager(
        appContext: Application,
        repository: QRepository,
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
