package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.user.UserService
import com.qonversion.android.sdk.internal.user.UserServiceImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesServiceImpl

internal object ServicesAssemblyImpl : ServicesAssembly {
    lateinit var miscAssembly: MiscAssembly

    override val userPropertiesService: UserPropertiesService
        get() = UserPropertiesServiceImpl(
            miscAssembly.requestConfigurator,
            miscAssembly.getApiInteractor(RetryPolicy.InfiniteExponential()),
            miscAssembly.userPropertiesMapper
        )

    override val userService: UserService
        get() = UserServiceImpl(
            miscAssembly.requestConfigurator,
            miscAssembly.getApiInteractor(RetryPolicy.Exponential()),
            miscAssembly.userMapper,
            miscAssembly.localStorage
        )

    override fun initialize(miscAssembly: MiscAssembly) {
        this.miscAssembly = miscAssembly
    }
}
