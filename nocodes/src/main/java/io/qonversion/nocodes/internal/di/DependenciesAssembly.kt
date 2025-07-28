package io.qonversion.nocodes.internal.di

import android.app.Application
import io.qonversion.nocodes.internal.di.controllers.ControllersAssembly
import io.qonversion.nocodes.internal.di.controllers.ControllersAssemblyImpl
import io.qonversion.nocodes.internal.di.mappers.MappersAssembly
import io.qonversion.nocodes.internal.di.mappers.MappersAssemblyImpl
import io.qonversion.nocodes.internal.di.misc.MiscAssembly
import io.qonversion.nocodes.internal.di.misc.MiscAssemblyImpl
import io.qonversion.nocodes.internal.di.network.NetworkAssembly
import io.qonversion.nocodes.internal.di.network.NetworkAssemblyImpl
import io.qonversion.nocodes.internal.di.services.ServicesAssembly
import io.qonversion.nocodes.internal.di.services.ServicesAssemblyImpl
import io.qonversion.nocodes.internal.di.storage.StorageAssembly
import io.qonversion.nocodes.internal.di.storage.StorageAssemblyImpl
import io.qonversion.nocodes.internal.dto.config.InternalConfig

internal class DependenciesAssembly(
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val networkAssembly: NetworkAssembly,
    private val miscAssembly: MiscAssembly,
    private val servicesAssembly: ServicesAssembly,
    private val controllersAssembly: ControllersAssembly
) : MiscAssembly by miscAssembly,
    StorageAssembly by storageAssembly,
    NetworkAssembly by networkAssembly,
    MappersAssembly by mappersAssembly,
    ServicesAssembly by servicesAssembly,
    ControllersAssembly by controllersAssembly {

    companion object {
        // For fragment dependencies
        internal lateinit var instance: DependenciesAssembly
    }

    class Builder(
        private val application: Application,
        private val internalConfig: InternalConfig
    ) {
        fun build(): DependenciesAssembly {
            val miscAssembly = MiscAssemblyImpl(application, internalConfig)
            val mappersAssembly = MappersAssemblyImpl()
            val storageAssembly = StorageAssemblyImpl(application)
            val networkAssembly =
                NetworkAssemblyImpl(internalConfig, mappersAssembly, storageAssembly, miscAssembly)
            val servicesAssembly = ServicesAssemblyImpl(
                mappersAssembly,
                networkAssembly,
                miscAssembly,
                application,
                internalConfig.primaryConfig.fallbackFileName
            )
            val controllersAssembly = ControllersAssemblyImpl(
                servicesAssembly,
                miscAssembly,
                mappersAssembly,
                internalConfig,
                application
            )

            instance = DependenciesAssembly(
                mappersAssembly,
                storageAssembly,
                networkAssembly,
                miscAssembly,
                servicesAssembly,
                controllersAssembly
            )

            return instance
        }
    }
}
