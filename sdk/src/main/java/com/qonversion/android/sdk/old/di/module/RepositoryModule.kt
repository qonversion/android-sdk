package com.qonversion.android.sdk.old.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.old.api.ApiErrorMapper
import com.qonversion.android.sdk.old.EnvironmentProvider
import com.qonversion.android.sdk.old.IncrementalDelayCalculator
import com.qonversion.android.sdk.old.QonversionConfig
import com.qonversion.android.sdk.old.QonversionRepository
import com.qonversion.android.sdk.old.api.Api
import com.qonversion.android.sdk.old.api.ApiHeadersProvider
import com.qonversion.android.sdk.old.api.ApiHelper
import com.qonversion.android.sdk.old.di.scope.ApplicationScope
import com.qonversion.android.sdk.old.logger.Logger
import com.qonversion.android.sdk.old.storage.PurchasesCache
import com.qonversion.android.sdk.old.storage.TokenStorage
import com.qonversion.android.sdk.old.storage.UserPropertiesStorage
import com.qonversion.android.sdk.old.storage.SharedPreferencesCache
import com.qonversion.android.sdk.old.validator.TokenValidator
import dagger.Module
import dagger.Provides
import retrofit2.Retrofit

@Module
class RepositoryModule {

    @ApplicationScope
    @Provides
    fun provideRepository(
        retrofit: Retrofit,
        environmentProvider: EnvironmentProvider,
        config: QonversionConfig,
        logger: Logger,
        purchasesCache: PurchasesCache,
        apiErrorMapper: ApiErrorMapper,
        sharedPreferences: SharedPreferences,
        delayCalculator: IncrementalDelayCalculator
    ): QonversionRepository {
        return QonversionRepository(
            retrofit.create(Api::class.java),
            environmentProvider,
            config,
            logger,
            purchasesCache,
            apiErrorMapper,
            sharedPreferences,
            delayCalculator
        )
    }

    @ApplicationScope
    @Provides
    fun provideTokenStorage(preferences: SharedPreferences): TokenStorage {
        return TokenStorage(
            preferences,
            TokenValidator()
        )
    }

    @ApplicationScope
    @Provides
    fun providePropertiesStorage(): UserPropertiesStorage {
        return UserPropertiesStorage()
    }

    @ApplicationScope
    @Provides
    fun provideEnvironment(context: Application): EnvironmentProvider {
        return EnvironmentProvider(context)
    }

    @ApplicationScope
    @Provides
    fun provideHeadersProvider(
        config: QonversionConfig,
        sharedPreferencesCache: SharedPreferencesCache
    ): ApiHeadersProvider {
        return ApiHeadersProvider(config, sharedPreferencesCache)
    }

    @ApplicationScope
    @Provides
    fun provideApiErrorMapper(
        apiHelper: ApiHelper
    ): ApiErrorMapper {
        return ApiErrorMapper(apiHelper)
    }
}
