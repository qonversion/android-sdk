package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.network.NetworkAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.user.service.UserServiceImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesServiceImpl

internal class ServicesAssemblyImpl(
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val networkAssembly: NetworkAssembly
) : ServicesAssembly {

    override fun userPropertiesService(): UserPropertiesService = UserPropertiesServiceImpl(
        networkAssembly.requestConfigurator(),
        networkAssembly.infiniteExponentialApiInteractor(),
        mappersAssembly.userPropertiesMapper()
    )

    override fun userService(): UserService = UserServiceImpl(
        networkAssembly.requestConfigurator(),
        networkAssembly.exponentialApiInteractor(),
        mappersAssembly.userMapper(),
        storageAssembly.sharedPreferencesStorage()
    )
}
