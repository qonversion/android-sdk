package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.common.mappers.error.ApiErrorMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilderImpl
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClientImpl
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfiguratorImpl
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import java.util.Locale
import kotlin.random.Random

private const val DEFAULT_BASE_URL = "https://api.qonversion.io/"

internal object MiscAssemblyImpl : MiscAssembly {

    override lateinit var application: Application

    override val internalConfig: InternalConfig
        get() = provideInternalConfig()

    override val logger: Logger
        get() = provideLogger()

    override val requestConfigurator: RequestConfigurator
        get() = provideRequestConfigurator()

    override val headerBuilder: HeaderBuilder
        get() = provideHeaderBuilder()

    override val sharedPreferences: SharedPreferences
        get() = provideSharedPreferences()

    override val localStorage: LocalStorage
        get() = provideLocalStorage()

    override val locale: Locale
        get() = provideLocale()

    override val networkClient: NetworkClient
        get() = provideNetworkClient()

    override val serializer: Serializer
        get() = provideSerializer()

    override val delayCalculator: RetryDelayCalculator
        get() = provideDelayCalculator()

    override val errorResponseMapper: ErrorResponseMapper
        get() = provideErrorResponseMapper()

    override val appLifecycleObserver: AppLifecycleObserver
        get() = provideAppLifecycleObserver()

    override fun init(application: Application) {
        this.application = application
    }

    fun provideInternalConfig(): InternalConfig {
        return InternalConfig
    }

    fun provideLogger(): Logger {
        return ConsoleLogger(internalConfig)
    }

    fun provideRequestConfigurator(): RequestConfigurator {
        return RequestConfiguratorImpl(
            provideHeaderBuilder(),
            DEFAULT_BASE_URL,
            provideInternalConfig(),
            provideInternalConfig()
        )
    }

    fun provideHeaderBuilder(): HeaderBuilder {
        return HeaderBuilderImpl(
            provideLocalStorage(),
            provideLocale(),
            provideInternalConfig(),
            provideInternalConfig(),
            provideInternalConfig()
        )
    }

    fun provideSharedPreferences(): SharedPreferences {
        return application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun provideLocalStorage(): LocalStorage {
        return SharedPreferencesStorage(provideSharedPreferences())
    }

    fun provideLocale(): Locale = Locale.getDefault()

    fun provideNetworkClient(): NetworkClient {
        return NetworkClientImpl(provideSerializer())
    }

    fun provideSerializer(): Serializer = JsonSerializer()

    fun provideDelayCalculator(): RetryDelayCalculator {
        return ExponentialDelayCalculator(Random)
    }

    fun provideErrorResponseMapper(): ErrorResponseMapper {
        return ApiErrorMapper()
    }

    fun provideAppLifecycleObserver(): AppLifecycleObserver {
        val instance = AppLifecycleObserverImpl()
        application.registerActivityLifecycleCallbacks(instance)

        return instance
    }
}
