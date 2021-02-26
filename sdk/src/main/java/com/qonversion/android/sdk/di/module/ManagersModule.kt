package com.qonversion.android.sdk.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.QUserPropertiesManager
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.storage.SharedPreferencesCache
import com.qonversion.android.sdk.storage.UserPropertiesStorage
import dagger.Module
import dagger.Provides

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
    fun provideUserPropertiesManager(
        appContext: Application,
        repository: QonversionRepository,
        propertiesStorage: UserPropertiesStorage,
        sharedPreferencesCache: SharedPreferencesCache
    ): QUserPropertiesManager {
        return QUserPropertiesManager(appContext, repository, propertiesStorage, sharedPreferencesCache)
    }

    @ApplicationScope
    @Provides
    fun provideUserPropertiesStorage(): UserPropertiesStorage {
        return UserPropertiesStorage()
    }
}
