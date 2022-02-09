package com.qonversion.android.sdk.internal.di.services

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideUserPropertiesService(): UserPropertiesService {
        return UserPropertiesServiceImpl(
            miscAssembly.requestConfigurator,
            miscAssembly.getApiInteractor(RetryPolicy.InfiniteExponential()),
            miscAssembly.userPropertiesMapper
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun provideUserService(): UserService {
        return UserServiceImpl(
            miscAssembly.requestConfigurator,
            miscAssembly.getApiInteractor(RetryPolicy.Exponential()),
            miscAssembly.userMapper,
            miscAssembly.localStorage
        )
    }
}
