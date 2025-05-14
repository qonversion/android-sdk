package io.qonversion.nocodes.interfaces

import io.qonversion.nocodes.dto.QScreenPresentationConfig

/**
 * The delegate is responsible for customizing screens representation.
 */
interface ScreenCustomizationDelegate {
    /**
     * The function should return the screen presentation configuration
     * used to present the first screen in the chain.
     * @param contextKey the context key of the screen, for which the configuration will be used.
     * @return screen presentation configuration.
     */
    fun getPresentationConfigurationForScreen(contextKey: String): QScreenPresentationConfig
}
