package io.qonversion.nocodes

import android.util.Log
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.internal.NoCodesInternal
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.interfaces.NoCodesShowScreenCallback
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
     * Show the screen using its ID.
     * @param screenId identifier of the screen which must be shown.
     * @param callback callback that is called when the screen is shown to a user.
     */
    fun showScreen(screenId: String, callback: NoCodesShowScreenCallback)

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
     * For example, you can use it to filter the Qonversion SDK logs and your app own logs together.
     *
     * You may set log tag both *after* Qonversion No-Codes SDK initializing with [NoCodes.setLogTag]
     * and *while* Qonversion No-Codes initializing with [NoCodes.initialize]
     *
     * @param logTag the desired log tag.
     */
    fun setLogTag(logTag: String)
}
