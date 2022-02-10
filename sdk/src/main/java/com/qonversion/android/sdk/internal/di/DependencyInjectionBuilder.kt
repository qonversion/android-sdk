package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssemblyImpl
import com.qonversion.android.sdk.internal.di.mappers.MappersAssemblyImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssemblyImpl
import com.qonversion.android.sdk.internal.di.network.NetworkAssemblyImpl
import com.qonversion.android.sdk.internal.di.services.ServicesAssemblyImpl
import com.qonversion.android.sdk.internal.di.storage.StorageAssemblyImpl

internal class DependencyInjectionBuilder(private val application: Application) {

    fun build(): DependencyInjection {
        val mappersAssembly = MappersAssemblyImpl()
        val miscAssembly = MiscAssemblyImpl(application)
        val storageAssembly = StorageAssemblyImpl(mappersAssembly, miscAssembly)
        val networkAssembly = NetworkAssemblyImpl(mappersAssembly, storageAssembly, miscAssembly)
        val servicesAssembly = ServicesAssemblyImpl(mappersAssembly, storageAssembly, networkAssembly)
        val controllersAssembly = ControllersAssemblyImpl(storageAssembly, miscAssembly, servicesAssembly)

        return DependencyInjection(
            mappersAssembly,
            storageAssembly,
            networkAssembly,
            miscAssembly,
            servicesAssembly,
            controllersAssembly
        )
    }
}
