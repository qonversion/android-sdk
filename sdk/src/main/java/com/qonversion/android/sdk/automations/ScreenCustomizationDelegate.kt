package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.automations.dto.QScreenPresentationConfig

/**
 * The delegate is responsible for customizing screens representation.
 */
interface ScreenCustomizationDelegate {
    /**
     * The function should return the screen presentation configuration
     * used to present the first screen in the chain.
     * @param screenId identifier of the screen, for which the configuration will be used.
     * @return screen presentation configuration.
     */
    fun getPresentationConfigurationForScreen(screenId: String): QScreenPresentationConfig
}
