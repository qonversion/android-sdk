package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.common.BASE_API_URL
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.ProductMapper
import com.qonversion.android.sdk.internal.common.mappers.SubscriptionMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ApiErrorMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractorImpl
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

    override val userMapper: UserMapper
        get() = provideUserMapper()

    override val userPurchaseMapper: UserPurchaseMapper
        get() = provideUserPurchaseMapper()

    override val entitlementMapper: EntitlementMapper
        get() = provideEntitlementMapper()

    override val productMapper: ProductMapper
        get() = provideProductMapper()

    override val subscriptionMapper: SubscriptionMapper
        get() = provideSubscriptionMapper()

    override val userPropertiesMapper: UserPropertiesMapper
        get() = provideUserPropertiesMapper()

    override fun init(application: Application) {
        this.application = application
    }

    override fun getApiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return provideApiInteractor(retryPolicy)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideInternalConfig(): InternalConfig {
        return InternalConfig
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideLogger(): Logger {
        return ConsoleLogger(internalConfig)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideRequestConfigurator(): RequestConfigurator {
        return RequestConfiguratorImpl(
            headerBuilder,
            BASE_API_URL,
            internalConfig,
            internalConfig
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideHeaderBuilder(): HeaderBuilder {
        return HeaderBuilderImpl(
            localStorage,
            locale,
            internalConfig,
            internalConfig,
            internalConfig
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideSharedPreferences(): SharedPreferences {
        return application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideLocalStorage(): LocalStorage {
        return SharedPreferencesStorage(sharedPreferences)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideLocale(): Locale = Locale.getDefault()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideNetworkClient(): NetworkClient {
        return NetworkClientImpl(serializer)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideSerializer(): Serializer = JsonSerializer()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideDelayCalculator(): RetryDelayCalculator {
        return ExponentialDelayCalculator(Random)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideErrorResponseMapper(): ErrorResponseMapper {
        return ApiErrorMapper()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideAppLifecycleObserver(): AppLifecycleObserver {
        val instance = AppLifecycleObserverImpl()
        application.registerActivityLifecycleCallbacks(instance)

        return instance
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideApiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            networkClient,
            delayCalculator,
            internalConfig,
            errorResponseMapper,
            retryPolicy
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideUserMapper(): UserMapper {
        return UserMapper(userPurchaseMapper, entitlementMapper)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideUserPurchaseMapper(): UserPurchaseMapper {
        return UserPurchaseMapper()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideEntitlementMapper(): EntitlementMapper {
        return EntitlementMapper()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideSubscriptionMapper(): SubscriptionMapper {
        return SubscriptionMapper()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideProductMapper(): ProductMapper {
        return ProductMapper()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideUserPropertiesMapper(): UserPropertiesMapper {
        return UserPropertiesMapper()
    }
}
