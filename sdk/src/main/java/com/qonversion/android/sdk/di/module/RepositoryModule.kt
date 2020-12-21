package com.qonversion.android.sdk.di.module

import android.app.Application
import androidx.preference.PreferenceManager
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.ApiHeadersProvider
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import com.qonversion.android.sdk.storage.TokenStorage
import com.qonversion.android.sdk.storage.UserPropertiesStorage
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
        logger: Logger,
        environmentProvider: EnvironmentProvider,
        config: QonversionConfig,
        requestQueue: RequestsQueue,
        apiHeadersProvider: ApiHeadersProvider
    ): QonversionRepository {
        return QonversionRepository(
            retrofit.create(Api::class.java),
            tokenStorage,
            propertiesStorage,
            environmentProvider,
            config.sdkVersion,
            config.trackingEnabled,
            config.key,
            logger,
            null,
            requestQueue,
            RequestValidator(),
            apiHeadersProvider
        )
    }

    @ApplicationScope
    @Provides
    fun provideTokenStorage(context: Application): TokenStorage {
        return TokenStorage(
            PreferenceManager.getDefaultSharedPreferences(context),
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
    fun provideRequestQueue(
        logger: Logger
    ): RequestsQueue {
        return RequestsQueue(logger)
    }

    @ApplicationScope
    @Provides
    fun provideHeadersProvider(
    ): ApiHeadersProvider {
        return ApiHeadersProvider()
    }
}