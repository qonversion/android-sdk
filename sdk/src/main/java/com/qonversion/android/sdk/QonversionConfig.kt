package com.qonversion.android.sdk

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import android.content.Context
import androidx.annotation.RawRes
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.application
import com.qonversion.android.sdk.internal.isDebuggable
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener

/**
 * This class contains all the available configurations for the initialization of Qonversion SDK.
 *
 * To create an instance, use the nested [Builder] class.
 *
 * You should pass the created instance to the [Qonversion.initialize] method.
 *
 * @see [The documentation](https://documentation.qonversion.io/docs/quickstart)
 */
class QonversionConfig internal constructor(
    internal val application: Application,
    internal val primaryConfig: PrimaryConfig,
    internal val cacheConfig: CacheConfig,
    internal val entitlementsUpdateListener: QEntitlementsUpdateListener?
) {

    /**
     * The builder of Qonversion configuration instance.
     *
     * This class contains a variety of methods to customize the Qonversion SDK behavior.
     * You can call them sequentially and call [build] finally to get the configuration instance.
     *
     * @constructor creates an instance of a builder
     * @param context the current context
     * @param projectKey your Project Key from Qonversion Dashboard
     * @param launchMode launch mode of the Qonversion SDK
     */
    class Builder(
        private val context: Context,
        private val projectKey: String,
        private val launchMode: QLaunchMode
    ) {
        internal var environment = QEnvironment.Production
        internal var entitlementsCacheLifetime = QEntitlementsCacheLifetime.Month
        internal var entitlementsUpdateListener: QEntitlementsUpdateListener? = null
        internal var proxyUrl: String? = null
        internal var isKidsMode: Boolean = false
        @RawRes
        internal var fallbackFileIdentifier: Int? = null

        /**
         * Set current application [QEnvironment]. Used to distinguish sandbox and production users.
         *
         * @param environment current environment.
         * @return builder instance for chain calls.
         */
        fun setEnvironment(environment: QEnvironment): Builder = apply {
            this.environment = environment
        }

        /**
         * Entitlements cache is used when there are problems with the Qonversion API
         * or internet connection. If so, Qonversion will return the last successfully loaded
         * entitlements. The current method allows you to configure how long that cache may be used.
         * The default value is [QEntitlementsCacheLifetime.Month].
         *
         * @param lifetime desired entitlements cache lifetime duration
         */
        fun setEntitlementsCacheLifetime(lifetime: QEntitlementsCacheLifetime): Builder = apply {
            this.entitlementsCacheLifetime = lifetime
        }

        /**
         * Sets fallback file identifier.
         * Fallback file will be used in rare cases of network connection or Qonversion API issues for new users without a cache available.
         * This allows purchases and entitlements to be processed for new users even if the Qonversion API faces issues.
         * This also makes it possible to receive remote configs for cases when the network connection is unavailable.
         * There is no need to use this function if you put qonversion_fallbacks.json into the `assets` folder.
         * Use this function only if you put qonversion_fallbacks.json into the `res/raw` folder.
         * In that case, `id` should look like `R.raw.qonversion_fallbacks`.
         *
         * @param id the identifier for the fallback file.
         *
         * @see [The documentation](https://documentation.qonversion.io/docs/system-reliability#fallback-files)
         */
        fun setFallbackFileIdentifier(@RawRes id: Int): Builder = apply {
            this.fallbackFileIdentifier = id
        }

        /**
         * Provide a listener to be notified about asynchronous user entitlements updates.
         *
         * Make sure you provide this listener for being up-to-date with the user entitlements.
         * Else you can lose some important updates. Also, please, consider that this listener
         * should live for the whole lifetime of the application.
         *
         * @param entitlementsUpdateListener listener to be called when entitlements update.
         * @return builder instance for chain calls.
         */
        fun setEntitlementsUpdateListener(entitlementsUpdateListener: QEntitlementsUpdateListener): Builder = apply {
            this.entitlementsUpdateListener = entitlementsUpdateListener
        }

        /**
         * Provide a URL to your proxy server which will redirect all the requests from the app
         * to our API. Please, contact us before using this feature.
         *
         * @param url your proxy server url
         * @return builder instance for chain calls.
         * @see [The documentation](https://documentation.qonversion.io/docs/custom-proxy-server-for-sdks)
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
         * Use this function to enable Qonversion SDK Kids mode.
         * With this mode activated, our SDK does not collect any information that violates Google Children’s Privacy Policy.
         */
        fun enableKidsMode(): Builder = apply {
            this.isKidsMode = true
        }

        /**
         * Generate [QonversionConfig] instance with all the provided configurations.
         * This method also validates some of the provided data.
         *
         * @throws IllegalStateException if unacceptable configuration was provided.
         * @return the complete [QonversionConfig] instance.
         */
        fun build(): QonversionConfig {
            if (projectKey.isBlank()) {
                throw IllegalStateException("Project key is empty")
            }
            if (environment === QEnvironment.Production && context.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Production for debug build.")
            } else if (environment === QEnvironment.Sandbox && !context.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Sandbox for release build.")
            }

            val primaryConfig = PrimaryConfig(projectKey, launchMode, environment, proxyUrl, isKidsMode)
            val cacheConfig = CacheConfig(entitlementsCacheLifetime, fallbackFileIdentifier)

            return QonversionConfig(
                context.application,
                primaryConfig,
                cacheConfig,
                entitlementsUpdateListener
            )
        }
    }
}
