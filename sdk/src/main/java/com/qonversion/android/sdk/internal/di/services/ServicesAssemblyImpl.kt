package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.network.NetworkAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.user.UserService
import com.qonversion.android.sdk.internal.user.UserServiceImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesServiceImpl

internal class ServicesAssemblyImpl(
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val networkAssembly: NetworkAssembly
) : ServicesAssembly {

    override val userPropertiesService: UserPropertiesService
        get() = UserPropertiesServiceImpl(
            networkAssembly.requestConfigurator,
            networkAssembly.getApiInteractor(RetryPolicy.InfiniteExponential()),
            mappersAssembly.userPropertiesMapper
        )

    override val userService: UserService
        get() = UserServiceImpl(
            networkAssembly.requestConfigurator,
            networkAssembly.getApiInteractor(RetryPolicy.Exponential()),
            mappersAssembly.userMapper,
            storageAssembly.sharedPreferencesStorage
        )
}
