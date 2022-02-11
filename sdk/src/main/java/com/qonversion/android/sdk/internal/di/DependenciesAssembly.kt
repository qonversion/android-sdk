package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssembly
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssemblyImpl
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.mappers.MappersAssemblyImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssemblyImpl
import com.qonversion.android.sdk.internal.di.network.NetworkAssembly
import com.qonversion.android.sdk.internal.di.network.NetworkAssemblyImpl
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssemblyImpl
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssemblyImpl

internal class DependenciesAssembly(
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val networkAssembly: NetworkAssembly,
    private val miscAssembly: MiscAssembly,
    private val servicesAssembly: ServicesAssembly,
    private val controllersAssembly: ControllersAssembly
) : ControllersAssembly by controllersAssembly,
    ServicesAssembly by servicesAssembly,
    MiscAssembly by miscAssembly,
    StorageAssembly by storageAssembly,
    NetworkAssembly by networkAssembly,
    MappersAssembly by mappersAssembly {

    class Builder(
        private val application: Application,
        private val internalConfig: InternalConfig
    ) {
        fun build(): DependenciesAssembly {
            val mappersAssembly = MappersAssemblyImpl()
            val miscAssembly = MiscAssemblyImpl(application, internalConfig)
            val storageAssembly = StorageAssemblyImpl(application, mappersAssembly, miscAssembly)
            val networkAssembly =
                NetworkAssemblyImpl(internalConfig, mappersAssembly, storageAssembly, miscAssembly)
            val servicesAssembly =
                ServicesAssemblyImpl(mappersAssembly, storageAssembly, networkAssembly)
            val controllersAssembly =
                ControllersAssemblyImpl(storageAssembly, miscAssembly, servicesAssembly)

            return DependenciesAssembly(
                mappersAssembly,
                storageAssembly,
                networkAssembly,
                miscAssembly,
                servicesAssembly,
                controllersAssembly
            )
        }
    }
}
