package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.ProductMapper
import com.qonversion.android.sdk.internal.common.mappers.SubscriptionMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import java.util.Locale

internal interface MiscAssembly {

    val application: Application

    val internalConfig: InternalConfig

    val logger: Logger

    val requestConfigurator: RequestConfigurator

    val headerBuilder: HeaderBuilder

    val sharedPreferences: SharedPreferences

    val localStorage: LocalStorage

    val locale: Locale

    val networkClient: NetworkClient

    val serializer: Serializer

    val delayCalculator: RetryDelayCalculator

    val errorResponseMapper: ErrorResponseMapper

    val appLifecycleObserver: AppLifecycleObserver

    val userMapper: UserMapper

    val userPurchaseMapper: UserPurchaseMapper

    val entitlementMapper: EntitlementMapper

    val productMapper: ProductMapper

    val subscriptionMapper: SubscriptionMapper

    val userPropertiesMapper: UserPropertiesMapper

    fun init(application: Application)

    fun getApiInteractor(retryPolicy: RetryPolicy): ApiInteractor
}
