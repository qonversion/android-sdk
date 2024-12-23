package io.qonversion.nocodes.internal.di.services

import io.qonversion.nocodes.internal.di.mappers.MappersAssembly
import io.qonversion.nocodes.internal.di.network.NetworkAssembly
import io.qonversion.nocodes.internal.screen.service.ScreenService
import io.qonversion.nocodes.internal.screen.service.ScreenServiceImpl

internal class ServicesAssemblyImpl(
    private val mappersAssembly: MappersAssembly,
    private val networkAssembly: NetworkAssembly
) : ServicesAssembly {

    override fun screenService(): ScreenService {
        return ScreenServiceImpl(
            networkAssembly.requestConfigurator(),
            networkAssembly.exponentialApiInteractor(),
            mappersAssembly.screenMapper()
        )
    }
}