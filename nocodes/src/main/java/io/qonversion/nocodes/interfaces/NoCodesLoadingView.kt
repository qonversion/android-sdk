package io.qonversion.nocodes.interfaces

/**
 * Interface for custom loading views displayed while NoCodes screens are loading.
 * Implementing classes must extend [android.view.View].
 */
interface NoCodesLoadingView {
    /**
     * Called when the loading view should start its loading animation.
     */
    fun startAnimating()

    /**
     * Called when the loading view should stop its loading animation.
     */
    fun stopAnimating()
}
