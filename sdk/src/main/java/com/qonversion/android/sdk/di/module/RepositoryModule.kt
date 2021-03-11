package com.qonversion.android.sdk.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.EnvironmentProvider
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.RequestsQueue
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.ApiHeadersProvider
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.*
import com.qonversion.android.sdk.validator.RequestValidator
import com.qonversion.android.sdk.validator.TokenValidator
import dagger.Module
import dagger.Provides
import retrofit2.Retrofit

@Module
class RepositoryModule {

    @ApplicationScope
    @Provides
    fun provideRepository(
        retrofit: Retrofit,
        tokenStorage: TokenStorage,
        propertiesStorage: PropertiesStorage,
        environmentProvider: EnvironmentProvider,
        config: QonversionConfig,
        logger: Logger,
        requestsQueue: RequestsQueue,
        requestValidator: RequestValidator,
        purchasesCache: PurchasesCache
    ): QonversionRepository {
        return QonversionRepository(
            retrofit.create(Api::class.java),
            tokenStorage,
            propertiesStorage,
            environmentProvider,
            config.sdkVersion,
            config.key,
            config.isDebugMode,
            logger,
            requestsQueue,
            requestValidator,
            purchasesCache
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
    fun providePropertiesStorage(): PropertiesStorage {
        return UserPropertiesStorage()
    }

    @ApplicationScope
    @Provides
    fun provideEnvironment(context: Application): EnvironmentProvider {
        return EnvironmentProvider(context)
    }

    @ApplicationScope
    @Provides
    fun provideRequestValidator(): RequestValidator {
        return RequestValidator()
    }

    @ApplicationScope
    @Provides
    fun provideRequestsQueue(logger: Logger): RequestsQueue {
        return RequestsQueue(logger)
    }

    @ApplicationScope
    @Provides
    fun provideHeadersProvider(config: QonversionConfig, sharedPreferencesCache: SharedPreferencesCache
    ): ApiHeadersProvider {
        return ApiHeadersProvider(config, sharedPreferencesCache)
    }
}