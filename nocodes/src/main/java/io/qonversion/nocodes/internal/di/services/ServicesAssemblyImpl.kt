package io.qonversion.nocodes.internal.di.services

import android.content.Context
import io.qonversion.nocodes.internal.common.FallbackConstants
import io.qonversion.nocodes.internal.di.mappers.MappersAssembly
import io.qonversion.nocodes.internal.di.misc.MiscAssembly
import io.qonversion.nocodes.internal.di.network.NetworkAssembly
import io.qonversion.nocodes.internal.screen.service.FallbackService
import io.qonversion.nocodes.internal.screen.service.FallbackServiceImpl
import io.qonversion.nocodes.internal.screen.service.ScreenService
import io.qonversion.nocodes.internal.screen.service.ScreenServiceImpl

internal class ServicesAssemblyImpl(
    private val mappersAssembly: MappersAssembly,
    private val networkAssembly: NetworkAssembly,
    private val miscAssembly: MiscAssembly,
    private val context: Context,
    private val fallbackFileName: String?
) : ServicesAssembly {

    override fun screenService(): ScreenService {
        return ScreenServiceImpl(
            networkAssembly.requestConfigurator(),
            networkAssembly.exponentialApiInteractor(),
            mappersAssembly.screenMapper(),
            fallbackService(),
            miscAssembly.logger()
        )
    }

    override fun fallbackService(): FallbackService? {
        val effectiveFileName = fallbackFileName ?: FallbackConstants.DEFAULT_FILE_NAME

        // Check if fallback file is available
        if (!FallbackService.isFallbackFileAvailable(effectiveFileName, context)) {
            return null
        }

        return FallbackServiceImpl(
            context,
            effectiveFileName,
            mappersAssembly.screenMapper(),
            miscAssembly.logger()
        )
    }
}
