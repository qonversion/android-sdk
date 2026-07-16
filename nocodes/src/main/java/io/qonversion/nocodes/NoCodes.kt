package io.qonversion.nocodes

import android.util.Log
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.dto.NoCodesTheme
import io.qonversion.nocodes.dto.QNoCodeScreen
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.NoCodesScreenLoadCallback
import io.qonversion.nocodes.interfaces.PurchaseDelegate
import io.qonversion.nocodes.interfaces.PurchaseDelegateWithCallbacks
import io.qonversion.nocodes.internal.NoCodesInternal
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.interfaces.CustomVariablesDelegate
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate

interface NoCodes {

    companion object {

        private var backingInstance: NoCodes? = null

        /**
         * Use this variable to get a current initialized instance of the Qonversion No-Codes SDK.
         * Please, use the property only after calling [NoCodes.initialize].
         * Otherwise, trying to access the variable will cause an exception.
         *
         * @return Current initialized instance of the Qonversion No-Codes SDK.
         * @throws UninitializedPropertyAccessException if the instance has not been initialized
         */
        @JvmStatic
        @get:JvmName("getSharedInstance")
        val shared: NoCodes
            get() = backingInstance ?: throw UninitializedPropertyAccessException(
                "Qonversion No-Codes has not been initialized. You should call " +
                        "the initialize method before accessing the shared instance of Qonversion No-Codes."
            )

        /**
         * An entry point to use Qonversion No-Codes SDK.
         * Call to initialize Qonversion No-Codes SDK with the required data.
         *
         * @param config a config that contains key SDK settings.
         * @return Initialized instance of the Qonversion No-Codes SDK.
         */
        @JvmStatic
        fun initialize(config: NoCodesConfig): NoCodes {
            backingInstance?.let {
                Log.e(
                    "Qonversion No-Codes", "Qonversion No-Codes has been initialized already. " +
                            "Multiple instances of Qonversion No-Codes are not supported now."
                )
                return it
            }

            val internalConfig = InternalConfig(config)
            val dependenciesAssembly = DependenciesAssembly.Builder(
                config.application,
                internalConfig
            ).build()
            return NoCodesInternal(internalConfig, dependenciesAssembly).also {
                backingInstance = it
            }
        }
    }

    /**
     * The delegate is receiving events from the No-Code screens.
     * Make sure the method is called before [NoCodes.showScreen].
     * You can also provide it during the initialization via [NoCodesConfig.Builder.setDelegate].
     *
     * @param delegate delegate to be called when any no-code screen event occurs.
     */
    fun setDelegate(delegate: NoCodesDelegate)

    /**
     * The delegate is responsible for customizing screens representation.
     * You can also provide it during the initialization via [NoCodesConfig.Builder.setScreenCustomizationDelegate].
     *
     * @param delegate delegate that would be called before opening Qonversion No-Code screens.
     */
    fun setScreenCustomizationDelegate(delegate: ScreenCustomizationDelegate)

    /**
     * The delegate should be used if you want to handle purchases and restore operations on your end.
     * If this delegate is provided, it will be used instead of the default Qonversion SDK purchase flow.
     * You can also provide it during the initialization via [NoCodesConfig.Builder.setPurchaseDelegate].
     *
     * @param delegate delegate responsible for handling purchases and restore operations.
     */
    fun setPurchaseDelegate(delegate: PurchaseDelegate)

    /**
     * The delegate should be used if you want to handle purchases and restore operations on your end.
     * If this delegate is provided, it will be used instead of the default Qonversion SDK purchase flow.
     * You can also provide it during the initialization via [NoCodesConfig.Builder.setPurchaseDelegate].
     *
     * @param delegate delegate responsible for handling purchases and restore operations.
     */
    fun setPurchaseDelegate(delegate: PurchaseDelegateWithCallbacks)

    /**
     * The delegate will be called each time a screen is about to be displayed
     * to get custom variables that will be injected into the screen's JavaScript context.
     * You can also provide it during the initialization via [NoCodesConfig.Builder.setCustomVariablesDelegate].
     *
     * @param delegate delegate responsible for providing custom variables.
     */
    fun setCustomVariablesDelegate(delegate: CustomVariablesDelegate)

    /**
     * Show the screen using its context key.
     * @param contextKey the context key of the screen which must be shown.
     */
    fun showScreen(contextKey: String)

    /**
     * Load a No-Code screen (from cache or network) without presenting it, so you can decide
     * whether to present it or show your own fallback UI before any SDK screen appears.
     *
     * This is an optional entry point, not the primary loader: screens with the "Preload" option
     * enabled in the No-Codes builder are preloaded automatically at SDK initialization,
     * and [showScreen] works on its own.
     *
     * A successful load warms the shared screens cache, so a following [showScreen] call
     * with the same context key renders from cache.
     *
     * The returned screen carries the typed default variables configured in the builder
     * ([QNoCodeScreen.defaultVariables]) — authored custom variables and product slots —
     * so you can read them by key before presenting.
     *
     * Unlike [showScreen], this call does not flush pending user properties, so the targeting
     * basis may differ slightly from a direct [showScreen] call.
     *
     * For Java or callback-based usage, see the [loadScreen] overload accepting
     * a [NoCodesScreenLoadCallback].
     *
     * @param contextKey the context key of the screen.
     * @return the loaded [QNoCodeScreen].
     * @throws NoCodesException with the code [io.qonversion.nocodes.error.ErrorCode.ScreenNotFound]
     * when no screen exists for the provided context key, or another code on a network
     * or other load failure.
     */
    @Throws(NoCodesException::class)
    suspend fun loadScreen(contextKey: String): QNoCodeScreen

    /**
     * Load a No-Code screen (from cache or network) without presenting it, so you can decide
     * whether to present it or show your own fallback UI before any SDK screen appears.
     *
     * This is a callback-based variant of the suspend [loadScreen] — see its documentation
     * for details. The [callback] is invoked on the main thread.
     *
     * @param contextKey the context key of the screen.
     * @param callback callback to be notified with the loaded screen or an error.
     */
    fun loadScreen(contextKey: String, callback: NoCodesScreenLoadCallback)

    /**
     * Use this function to close all No-Code Screens.
     */
    fun close()

    /**
     * Define the level of the logs that the SDK prints.
     * The more strict the level is, the less logs will be written.
     * For example, setting the log level as Warning will disable all info and verbose logs.
     *
     * You may set log level both *after* Qonversion No-Codes SDK initializing with [NoCodes.setLogLevel]
     * and *while* Qonversion No-Codes initializing with [NoCodes.initialize]
     *
     * See [LogLevel] for details.
     *
     * @param logLevel the desired allowed log level.
     */
    fun setLogLevel(logLevel: LogLevel)

    /**
     * Define the log tag that the Qonversion No-Codes SDK will print with every log message.
     * For example, you can use it to filter the Qonversion No-Codes SDK logs and your app own logs together.
     *
     * You may set log tag both *after* Qonversion No-Codes SDK initializing with [NoCodes.setLogTag]
     * and *while* Qonversion No-Codes initializing with [NoCodes.initialize]
     *
     * @param logTag the desired log tag.
     */
    fun setLogTag(logTag: String)

    /**
     * Set a custom locale for No-Code screens localization.
     * If set, this locale will take priority over the system default locale when determining
     * which localization to show on No-Code screens.
     * The locale should be in standard format (e.g., "en", "en-US", "de", "de-DE").
     *
     * You may set locale both *after* Qonversion No-Codes SDK initializing with [NoCodes.setLocale]
     * and *while* Qonversion No-Codes initializing via [NoCodesConfig.Builder.setLocale]
     *
     * Pass null to reset to system default locale.
     *
     * @param locale the custom locale code, or null to use system default.
     */
    fun setLocale(locale: String?)

    /**
     * Set the theme mode for No-Code screens.
     * Controls how screens adapt to light/dark themes.
     *
     * You may set theme both *after* Qonversion No-Codes SDK initializing with [NoCodes.setTheme]
     * and *while* Qonversion No-Codes initializing via [NoCodesConfig.Builder.setTheme]
     *
     * @param theme the desired theme mode. Use [NoCodesTheme.Auto] to follow device settings,
     *              [NoCodesTheme.Light] to force light theme, or [NoCodesTheme.Dark] to force dark theme.
     */
    fun setTheme(theme: NoCodesTheme)
}
