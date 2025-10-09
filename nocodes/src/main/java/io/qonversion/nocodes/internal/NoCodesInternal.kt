package io.qonversion.nocodes.internal

import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.dto.config.NoCodesDelegateWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal class NoCodesInternal(
    private val internalConfig: InternalConfig,
    dependenciesAssembly: DependenciesAssembly,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : NoCodes {

    private val screenController = dependenciesAssembly.screenController()
    private val screenService = dependenciesAssembly.screenService()
    private val logger = dependenciesAssembly.logger()

    init {
        // Automatic screen preloading during initialization
        scope.launch {
            try {
                val preloadedScreens = screenService.preloadScreens()
                logger.info("NoCodesInternal -> Successfully preloaded ${preloadedScreens.size} screens during initialization")
            } catch (e: Exception) {
                logger.warn("NoCodesInternal -> Failed to preload screens during initialization: ${e.message}")
            }
        }
    }

    override fun setDelegate(delegate: NoCodesDelegate) {
        internalConfig.noCodesDelegate = NoCodesDelegateWrapper(delegate)
    }

    override fun setScreenCustomizationDelegate(delegate: ScreenCustomizationDelegate) {
        internalConfig.screenCustomizationDelegate = WeakReference(delegate)
    }

    override fun showScreen(contextKey: String) {
        scope.launch {
            screenController.showScreen(contextKey)
        }
    }

    override fun close() {
        screenController.close()
    }

    override fun setLogLevel(logLevel: LogLevel) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logLevel = logLevel)
    }

    override fun setLogTag(logTag: String) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logTag = logTag)
    }
}
