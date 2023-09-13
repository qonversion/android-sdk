package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.api.ApiErrorMapper
import com.qonversion.android.sdk.internal.EnvironmentProvider
import com.qonversion.android.sdk.internal.IncrementalDelayCalculator
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.repository.DefaultRepository
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.api.ApiHeadersProvider
import com.qonversion.android.sdk.internal.api.ApiHelper
import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.repository.RepositoryWithRateLimits
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.internal.storage.TokenStorage
import com.qonversion.android.sdk.internal.storage.UserPropertiesStorage
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.qonversion.android.sdk.internal.validator.TokenValidator
import dagger.Module
import dagger.Provides
import retrofit2.Retrofit

@Module
internal class RepositoryModule {

    @ApplicationScope
    @Provides
    fun provideRepository(
        retrofit: Retrofit,
        environmentProvider: EnvironmentProvider,
        config: InternalConfig,
        logger: Logger,
        purchasesCache: PurchasesCache,
        apiErrorMapper: ApiErrorMapper,
        sharedPreferences: SharedPreferences,
        delayCalculator: IncrementalDelayCalculator
    ): QRepository {
        return RepositoryWithRateLimits(
            provideQonversionRepository(
                retrofit,
                environmentProvider,
                config,
                logger,
                purchasesCache,
                apiErrorMapper,
                sharedPreferences,
                delayCalculator
            )
        )
    }

    @ApplicationScope
    @Provides
    fun provideQonversionRepository(
        retrofit: Retrofit,
        environmentProvider: EnvironmentProvider,
        config: InternalConfig,
        logger: Logger,
        purchasesCache: PurchasesCache,
        apiErrorMapper: ApiErrorMapper,
        sharedPreferences: SharedPreferences,
        delayCalculator: IncrementalDelayCalculator
    ): DefaultRepository {
        return DefaultRepository(
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
        config: InternalConfig,
        sharedPreferencesCache: SharedPreferencesCache,
        environmentProvider: EnvironmentProvider
    ): ApiHeadersProvider {
        return ApiHeadersProvider(config, sharedPreferencesCache, environmentProvider)
    }

    @ApplicationScope
    @Provides
    fun provideApiErrorMapper(
        apiHelper: ApiHelper
    ): ApiErrorMapper {
        return ApiErrorMapper(apiHelper)
    }
}
