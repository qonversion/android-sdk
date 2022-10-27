package com.qonversion.android.sdk

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.Store
import android.content.Context
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.dto.config.StoreConfig
import com.qonversion.android.sdk.internal.application
import com.qonversion.android.sdk.internal.isDebuggable

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
    internal val storeConfig: StoreConfig
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
     * @param launchMode launch mode of the Qonversion SDK todo add link
     * @param store the store used for purchases (only [Store.GooglePlay] is supported for now)
     */
    class Builder @JvmOverloads constructor(
        private val context: Context,
        private val projectKey: String,
        private val launchMode: LaunchMode,
        private val store: Store = Store.GooglePlay
    ) {
        internal var environment = Environment.Production
        internal var shouldConsumePurchases = true

        /**
         * Set current application [Environment]. Used to distinguish sandbox and production users.
         *
         * @param environment current environment.
         * @return builder instance for chain calls.
         */
        fun setEnvironment(environment: Environment): Builder = apply {
            this.environment = environment
        }

        /**
         * Define should Qonversion consume purchases itself or not.
         * You may need to consume purchases yourself if you want to add custom handling of them
         * before it, for example, send the purchase to API or hand over coins to the user.
         * If set to false make sure that you call [Qonversion.consume] for purchases yourself.
         * todo fix method link above when it will be implemented
         *
         * The flag makes sense only for [LaunchMode.Infrastructure].
         *
         * @param shouldConsumePurchases if true, the Qonversion will consume purchases itself. If false - it won't.
         * @return builder instance for chain calls.
         */
        fun setShouldConsumePurchases(shouldConsumePurchases: Boolean): Builder = apply {
            this.shouldConsumePurchases = shouldConsumePurchases
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
            if (environment === Environment.Production && context.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Production for debug build.")
            } else if (environment === Environment.Sandbox && !context.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Sandbox for release build.")
            }

            val primaryConfig = PrimaryConfig(projectKey, launchMode, environment)
            val storeConfig = StoreConfig(store, shouldConsumePurchases)

            return QonversionConfig(
                context.application,
                primaryConfig,
                storeConfig
            )
        }
    }
}
