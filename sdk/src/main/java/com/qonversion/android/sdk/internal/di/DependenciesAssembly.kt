package com.qonversion.android.sdk.internal.di

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.di.cacher.CacherAssembly
import com.qonversion.android.sdk.internal.di.cacher.CacherAssemblyImpl
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
    @VisibleForTesting val mappersAssembly: MappersAssembly,
    @VisibleForTesting val storageAssembly: StorageAssembly,
    @VisibleForTesting val networkAssembly: NetworkAssembly,
    @VisibleForTesting val miscAssembly: MiscAssembly,
    @VisibleForTesting val servicesAssembly: ServicesAssembly,
    @VisibleForTesting val controllersAssembly: ControllersAssembly,
    @VisibleForTesting val cacherAssembly: CacherAssembly
) : ControllersAssembly by controllersAssembly,
    ServicesAssembly by servicesAssembly,
    MiscAssembly by miscAssembly,
    StorageAssembly by storageAssembly,
    NetworkAssembly by networkAssembly,
    MappersAssembly by mappersAssembly,
    CacherAssembly by cacherAssembly {

    class Builder(
        private val application: Application,
        private val internalConfig: InternalConfig
    ) {
        fun build(): DependenciesAssembly {
            val miscAssembly = MiscAssemblyImpl(application, internalConfig)
            val mappersAssembly = MappersAssemblyImpl(miscAssembly)
            val storageAssembly = StorageAssemblyImpl(application, mappersAssembly, miscAssembly)
            val networkAssembly =
                NetworkAssemblyImpl(internalConfig, mappersAssembly, storageAssembly, miscAssembly)
            val servicesAssembly =
                ServicesAssemblyImpl(mappersAssembly, networkAssembly)
            val cacherAssembly =
                CacherAssemblyImpl(storageAssembly, mappersAssembly, miscAssembly, internalConfig)
            val controllersAssembly =
                ControllersAssemblyImpl(
                    storageAssembly,
                    miscAssembly,
                    servicesAssembly,
                    cacherAssembly
                )

            return DependenciesAssembly(
                mappersAssembly,
                storageAssembly,
                networkAssembly,
                miscAssembly,
                servicesAssembly,
                controllersAssembly,
                cacherAssembly
            )
        }
    }
}
