package io.qonversion.nocodes.interfaces

import android.view.View
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

    /**
     * Returns a custom loading view to display while the NoCodes screen is loading.
     * The returned View must implement [NoCodesLoadingView].
     * If null is returned, the default skeleton loading view will be used.
     *
     * @return a View implementing NoCodesLoadingView, or null for default skeleton
     */
    fun noCodesCustomLoadingView(): View? = null
}
