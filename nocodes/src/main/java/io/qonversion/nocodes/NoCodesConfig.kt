package io.qonversion.nocodes

import android.app.Application
import android.content.Context
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.PurchaseHandlerDelegate
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate
import io.qonversion.nocodes.internal.dto.config.LoggerConfig
import io.qonversion.nocodes.internal.dto.config.NetworkConfig
import io.qonversion.nocodes.internal.dto.config.PrimaryConfig

private const val DEFAULT_LOG_TAG = "Qonversion No-Codes"

/**
 * This class contains all the available configurations for the initialization of Qonversion No-Codes SDK.
 *
 * To create an instance, use the nested [Builder] class.
 *
 * You should pass the created instance to the [NoCodes.initialize] method.
 *
 * @see [The documentation](https://documentation.qonversion.io/docs/getting-started-with-no-code-screens/)
 */
class NoCodesConfig internal constructor(
    internal val application: Application,
    internal val primaryConfig: PrimaryConfig,
    internal val networkConfig: NetworkConfig,
    internal val loggerConfig: LoggerConfig,
    internal val noCodesDelegate: NoCodesDelegate?,
    internal val screenCustomizationDelegate: ScreenCustomizationDelegate?,
    internal val purchaseHandlerDelegate: PurchaseHandlerDelegate?,
) {

    /**
     * The builder of Qonversion No-Codes configuration instance.
     *
     * This class contains a variety of methods to customize the Qonversion No-Codes SDK behavior.
     * You can call them sequentially and call [build] finally to get the configuration instance.
     *
     * @constructor creates an instance of a builder
     * @param context the current context
     * @param projectKey your Project Key from Qonversion Dashboard
     */
    class Builder(
        private val context: Context,
        private val projectKey: String,
    ) {
        private var noCodesDelegate: NoCodesDelegate? = null
        private var screenCustomizationDelegate: ScreenCustomizationDelegate? = null
        private var purchaseHandlerDelegate: PurchaseHandlerDelegate? = null
        private var proxyUrl: String? = null
        private var logLevel = LogLevel.Info
        private var logTag = DEFAULT_LOG_TAG
        private var customFallbackFileName: String? = null

        /**
         * Provide a delegate to be notified about the no-code screens events.
         * You can also provide it later via [NoCodes.setDelegate].
         *
         * @param noCodesDelegate delegate to be called when any no-code screen event occurs.
         * @return builder instance for chain calls.
         */
        fun setDelegate(noCodesDelegate: NoCodesDelegate): Builder = apply {
            this.noCodesDelegate = noCodesDelegate
        }

        /**
         * Provide a delegate to customize screens representation.
         * You can also provide it later via [NoCodes.setScreenCustomizationDelegate].
         *
         * @param screenCustomizationDelegate delegate responsible for customizing screens representation.
         * @return builder instance for chain calls.
         */
        fun setScreenCustomizationDelegate(screenCustomizationDelegate: ScreenCustomizationDelegate): Builder = apply {
            this.screenCustomizationDelegate = screenCustomizationDelegate
        }

        /**
         * Provide a delegate to handle No-Codes purchase and restore operations on your own.
         * If this delegate is provided, it will be used instead of the default Qonversion SDK
         * purchase and restore flows.
         *
         * @param purchaseHandlerDelegate delegate responsible for handling purchase and restores operations.
         * @return builder instance for chain calls.
         */
        fun setPurchaseHandlerDelegate(purchaseHandlerDelegate: PurchaseHandlerDelegate): Builder = apply {
            this.purchaseHandlerDelegate = purchaseHandlerDelegate
        }

        /**
         * Provide a URL to your proxy server which will redirect all the requests from the No-Codes
         * SDK to our API. Please, contact us before using this feature.
         *
         * @param url your proxy server url
         * @return builder instance for chain calls.
         */
        fun setProxyURL(url: String): Builder = apply {
            proxyUrl = url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                proxyUrl = "https://$proxyUrl"
            }

            if (!url.endsWith("/")) {
                proxyUrl += "/"
            }
        }

        /**
         * Define the level of the logs that the SDK prints.
         * The more strict the level is, the less logs will be written.
         * For example, setting the log level as Warning will disable all info and verbose logs.
         *
         * @param logLevel the desired allowed log level.
         * @return builder instance for chain calls.
         */
        fun setLogLevel(logLevel: LogLevel): Builder = apply {
            this.logLevel = logLevel
        }

        /**
         * Define the log tag that the Qonversion No-Codes SDK will print with every log message.
         * For example, you can use it to filter the Qonversion No-Codes SDK logs and your app own logs together.
         *
         * @param logTag the desired log tag.
         * @return builder instance for chain calls.
         */
        fun setLogTag(logTag: String): Builder = apply {
            this.logTag = logTag
        }

        /**
         * Set a custom fallback file name for offline scenarios.
         * This file should be placed in the assets folder and will be used when network is unavailable.
         * If not set, the default file name "nocodes_fallbacks.json" will be used.
         *
         * @param fileName the custom fallback file name.
         * @return builder instance for chain calls.
         */
        fun setCustomFallbackFileName(fileName: String): Builder = apply {
            this.customFallbackFileName = fileName
        }

        /**
         * Generate [NoCodesConfig] instance with all the provided configurations.
         * This method also validates some of the provided data.
         *
         * @throws IllegalStateException if unacceptable configuration was provided.
         * @return the complete [NoCodesConfig] instance.
         */
        fun build(): NoCodesConfig {
            if (projectKey.isBlank()) {
                throw IllegalStateException("Project key is empty")
            }

            val primaryConfig = PrimaryConfig(projectKey, customFallbackFileName = customFallbackFileName)
            val networkConfig = NetworkConfig(proxyUrl)
            val loggerConfig = LoggerConfig(logLevel, logTag)

            return NoCodesConfig(
                context.applicationContext as Application,
                primaryConfig,
                networkConfig,
                loggerConfig,
                noCodesDelegate,
                screenCustomizationDelegate,
                purchaseHandlerDelegate,
            )
        }
    }
}
