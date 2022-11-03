package com.qonversion.android.sdk

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import android.content.Context
import com.qonversion.android.sdk.dto.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.application
import com.qonversion.android.sdk.internal.isDebuggable
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener

/**
 * This class contains all the available configurations for the initialization of Qonversion SDK.
 *
 * To create an instance, use the nested [Builder] class.
 *
 * You should pass the created instance to the [Qonversion.initialize] method.
 *
 * @see [The documentation](https://documentation.qonversion.io/v3.0/docs/configuring-the-sdks)
 */
class QonversionConfig internal constructor(
    internal val application: Application,
    internal val primaryConfig: PrimaryConfig,
    internal val cacheConfig: CacheConfig,
    internal val entitlementsUpdateListener: EntitlementsUpdateListener?
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
        internal var entitlementsUpdateListener: EntitlementsUpdateListener? = null

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
         * Provide a listener to be notified about asynchronous user entitlements updates.
         *
         * Make sure you provide this listener for being up-to-date with the user entitlements.
         * Else you can lose some important updates. Also, please, consider that this listener
         * should live for the whole lifetime of the application.
         *
         * @param entitlementsUpdateListener listener to be called when entitlements update.
         * @return builder instance for chain calls.
         */
        fun setEntitlementsUpdateListener(entitlementsUpdateListener: EntitlementsUpdateListener): Builder = apply {
            this.entitlementsUpdateListener = entitlementsUpdateListener
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

            val primaryConfig = PrimaryConfig(projectKey, launchMode, environment)
            val cacheConfig = CacheConfig(entitlementsCacheLifetime)

            return QonversionConfig(
                context.application,
                primaryConfig,
                cacheConfig,
                entitlementsUpdateListener
            )
        }
    }
}
