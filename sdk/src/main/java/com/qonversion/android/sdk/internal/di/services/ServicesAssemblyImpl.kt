package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.dto.Entitlement
import com.qonversion.android.sdk.dto.Product
import com.qonversion.android.sdk.dto.Subscription
import com.qonversion.android.sdk.dto.UserPurchase
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.common.mappers.ProductMapper
import com.qonversion.android.sdk.internal.common.mappers.SubscriptionMapper
import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractorImpl
import com.qonversion.android.sdk.internal.user.UserService
import com.qonversion.android.sdk.internal.user.UserServiceImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesServiceImpl

internal object ServicesAssemblyImpl : ServicesAssembly {
    lateinit var miscAssembly: MiscAssembly

    override val userPropertiesService: UserPropertiesService
        get() = provideUserPropertiesService()

    override val userService: UserService
        get() = provideUserService()

    override fun init(miscAssembly: MiscAssembly) {
        this.miscAssembly = miscAssembly
    }

    fun provideUserPropertiesService(): UserPropertiesService {
        return UserPropertiesServiceImpl(
            miscAssembly.requestConfigurator,
            provideUserPropertiesApiInteractor(RetryPolicy.InfiniteExponential()),
            provideUserPropertiesMapper()
        )
    }

    fun provideUserPropertiesApiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            miscAssembly.networkClient,
            miscAssembly.delayCalculator,
            miscAssembly.internalConfig,
            miscAssembly.errorResponseMapper,
            retryPolicy
        )
    }

    fun provideUserPropertiesMapper(): UserPropertiesMapper {
        return UserPropertiesMapper()
    }

    fun provideUserService(): UserService {
        return UserServiceImpl(
            miscAssembly.requestConfigurator,
            provideUserApiInteractor(RetryPolicy.Exponential()),
            provideUserMapper(),
            miscAssembly.localStorage
        )
    }

    fun provideUserApiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            miscAssembly.networkClient,
            miscAssembly.delayCalculator,
            miscAssembly.internalConfig,
            miscAssembly.errorResponseMapper,
            retryPolicy
        )
    }

    fun provideUserMapper(): UserMapper {
        return UserMapper(providePurchasesMapper(), provideEntitlementMapper())
    }

    fun providePurchasesMapper(): Mapper<UserPurchase> {
        return UserPurchaseMapper()
    }

    fun provideEntitlementMapper(): Mapper<Entitlement> {
        return EntitlementMapper()
    }

    fun provideSubscriptionMapper(): Mapper<Subscription> {
        return SubscriptionMapper()
    }

    fun provideProductMapper(): Mapper<Product> {
        return ProductMapper()
    }
}
