package io.qonversion.nocodes.internal.di.services

import android.content.Context
import io.qonversion.nocodes.internal.di.mappers.MappersAssembly
import io.qonversion.nocodes.internal.di.misc.MiscAssembly
import io.qonversion.nocodes.internal.di.network.NetworkAssembly
import io.qonversion.nocodes.internal.screen.service.FallbackService
import io.qonversion.nocodes.internal.screen.service.FallbackServiceImpl
import io.qonversion.nocodes.internal.screen.service.ScreenService
import io.qonversion.nocodes.internal.screen.service.ScreenServiceImpl
import io.qonversion.nocodes.internal.utils.FallbackUtils

internal class ServicesAssemblyImpl(
    private val context: Context,
    private val mappersAssembly: MappersAssembly,
    private val networkAssembly: NetworkAssembly,
    private val miscAssembly: MiscAssembly,
    private val effectiveFallbackFileName: String
) : ServicesAssembly {

    private val screenServiceInstance by lazy {
        ScreenServiceImpl(
            networkAssembly.requestConfigurator(),
            networkAssembly.exponentialApiInteractor(),
            mappersAssembly.screenMapper(),
            fallbackService(),
            miscAssembly.logger()
        )
    }

    override fun screenService(): ScreenService {
        return screenServiceInstance
    }

    override fun fallbackService(): FallbackService? {
        // Check if fallback file is available
        if (!FallbackUtils.isFallbackFileAvailable(effectiveFallbackFileName, context)) {
            return null
        }

        return FallbackServiceImpl(
            context,
            effectiveFallbackFileName,
            mappersAssembly.screenMapper(),
            miscAssembly.logger()
        )
    }
}
