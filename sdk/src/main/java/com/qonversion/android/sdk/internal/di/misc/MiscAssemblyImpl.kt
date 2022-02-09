package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
        get() = InternalConfig

    override val logger: Logger
        get() = ConsoleLogger(internalConfig)

    override val requestConfigurator: RequestConfigurator
        get() = RequestConfiguratorImpl(
            headerBuilder,
            BASE_API_URL,
            internalConfig,
            internalConfig
        )

    override val headerBuilder: HeaderBuilder
        get() = HeaderBuilderImpl(
            localStorage,
            locale,
            internalConfig,
            internalConfig,
            internalConfig
        )

    override val sharedPreferences: SharedPreferences
        get() = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val localStorage: LocalStorage
        get() = SharedPreferencesStorage(sharedPreferences)

    override val locale: Locale
        get() = Locale.getDefault()

    override val networkClient: NetworkClient
        get() = NetworkClientImpl(jsonSerializer)

    override val jsonSerializer: Serializer
        get() = JsonSerializer()

    override val exponentialDelayCalculator: RetryDelayCalculator
        get() = ExponentialDelayCalculator(Random)

    override val errorResponseMapper: ErrorResponseMapper
        get() = ApiErrorMapper()

    override val appLifecycleObserver: AppLifecycleObserver
        get() {
            val instance = AppLifecycleObserverImpl()
            application.registerActivityLifecycleCallbacks(instance)
            return instance
        }

    override fun getApiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            networkClient,
            exponentialDelayCalculator,
            internalConfig,
            errorResponseMapper,
            retryPolicy
        )
    }

    override val userMapper: UserMapper
        get() = UserMapper(userPurchaseMapper, entitlementMapper)

    override val userPurchaseMapper: UserPurchaseMapper
        get() = UserPurchaseMapper()

    override val entitlementMapper: EntitlementMapper
        get() = EntitlementMapper()

    override val productMapper: ProductMapper
        get() = ProductMapper()

    override val subscriptionMapper: SubscriptionMapper
        get() = SubscriptionMapper()

    override val userPropertiesMapper: UserPropertiesMapper
        get() = UserPropertiesMapper()

    override fun initialize(application: Application) {
        this.application = application
    }
}
