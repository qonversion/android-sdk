package io.qonversion.nocodes.interfaces

/**
 * Delegate responsible for providing custom variables for No-Codes screens.
 * Custom variables are injected into the screen's JavaScript context
 * and can be used to influence content displayed on the screen.
 */
interface CustomVariablesDelegate {

    /**
     * Provide custom variables for a specific screen identified by context key.
     * Called each time a screen is about to be displayed.
     *
     * @param contextKey the context key of the screen being loaded.
     * @return a map of custom variables to inject into the screen.
     */
    fun getCustomVariables(contextKey: String): Map<String, String>
}
