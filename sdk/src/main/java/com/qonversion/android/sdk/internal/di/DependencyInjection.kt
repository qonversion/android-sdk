package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssembly
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssemblyImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssemblyImpl

import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssemblyImpl

object DependencyInjection :
    ControllersAssembly by ControllersAssemblyImpl,
    ServicesAssembly by ServicesAssemblyImpl,
    MiscAssembly by MiscAssemblyImpl {

    override fun init(application: Application) {
        MiscAssemblyImpl.init(application)
        ServicesAssemblyImpl.init(MiscAssemblyImpl)
        ControllersAssemblyImpl.init(MiscAssemblyImpl, ServicesAssemblyImpl)
    }
}
