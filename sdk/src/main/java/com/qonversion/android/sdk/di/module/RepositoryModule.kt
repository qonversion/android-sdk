package com.qonversion.android.sdk.di.module

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.BackendErrorMapper
import com.qonversion.android.sdk.EnvironmentProvider
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.ApiHeadersProvider
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.*
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
        environmentProvider: EnvironmentProvider,
        config: QonversionConfig,
        logger: Logger,
        purchasesCache: PurchasesCache,
        backendErrorMapper: BackendErrorMapper
    ): QonversionRepository {
        return QonversionRepository(
            retrofit.create(Api::class.java),
            environmentProvider,
            config.sdkVersion,
            config.key,
            config.isDebugMode,
            logger,
            purchasesCache,
            backendErrorMapper
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
        config: QonversionConfig, sharedPreferencesCache: SharedPreferencesCache
    ): ApiHeadersProvider {
        return ApiHeadersProvider(config, sharedPreferencesCache)
    }

    @ApplicationScope
    @Provides
    fun provideBackendErrorMapper(
    ): BackendErrorMapper {
        return BackendErrorMapper()
    }

}