package com.qonversion.android.sdk

import android.util.Log
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.Store
import android.app.Application
import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.utils.isDebuggable

private const val DEFAULT_LOG_TAG = "Qonversion"

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
    val application: Application,
    val primaryConfig: PrimaryConfig,
    val storeConfig: StoreConfig,
    val loggerConfig: LoggerConfig,
    val networkConfig: NetworkConfig,
    val cacheLifetime: CacheLifetime
) {

    /**
     * The builder of Qonversion configuration instance.
     *
     * This class contains a variety of methods to customize the Qonversion SDK behavior.
     * You can call them sequentially and call [build] finally to get the configuration instance.
     *
     * @constructor creates an instance of a builder
     * @param application the instance of the current running application
     * @param projectKey your Qonversion Project Key
     * @param launchMode launch mode of the Qonversion SDK
     * @param store the store used for purchases (only [Store.GooglePlay] is supported for now)
     */
    class Builder @JvmOverloads constructor(
        private val application: Application,
        private val projectKey: String,
        private val launchMode: LaunchMode,
        private val store: Store = Store.GooglePlay
    ) {
        internal var environment = Environment.Production
        internal var logLevel = LogLevel.Info
        internal var logTag = DEFAULT_LOG_TAG
        internal var cacheLifetime = CacheLifetime.ThreeDays
        internal var shouldConsumePurchases = true

        /**
         * Set current application environment. Used to distinguish sandbox and production users.
         *
         * @param environment current environment.
         * @return builder instance for chain calls.
         */
        fun setEnvironment(environment: Environment): Builder = apply {
            this.environment = environment
        }

        /**
         * Define the maximum lifetime of the data cached by Qonversion.
         * It means that cached data won't be used if it is elder than the provided duration.
         * By the way it doesn't mean that cache will live exactly the provided time.
         * It may be updated earlier.
         *
         * @param cacheLifetime the desired lifetime of Qonversion caches.
         * @return builder instance for chain calls.
         */
        fun setCacheLifetime(cacheLifetime: CacheLifetime): Builder = apply {
            this.cacheLifetime = cacheLifetime
        }

        /**
         * Define the log level from which all the messages will be written.
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
         * Define tag value provided to all Qonversion logs.
         *
         * @param logTag the desired log tag.
         * @return builder instance for chain calls.
         */
        fun setLogTag(logTag: String): Builder = apply {
            this.logTag = logTag
        }

        /**
         * Define should Qonversion consume purchases on its own or not.
         * If set to false make sure that you consume purchases yourself.
         *
         * The flag makes sense only for [LaunchMode.InfrastructureMode].
         *
         * @param shouldConsumePurchases if true, the Qonversion will consume purchases. If false - it won't.
         * @return builder instance for chain calls.
         */
        fun setShouldConsumePurchases(shouldConsumePurchases: Boolean): Builder = apply {
            this.shouldConsumePurchases = shouldConsumePurchases
        }

        /**
         * Generate [QonversionConfig] instance with all the provided configurations.
         * This method also validates some of provided data.
         *
         * @throws QonversionException if unacceptable configuration was provided.
         * @return the complete [QonversionConfig] instance.
         */
        @Throws(QonversionException::class)
        fun build(): QonversionConfig {
            if (projectKey.isBlank()) {
                throw QonversionException(ErrorCode.ConfigPreparation, "Project key is empty")
            }
            if (environment === Environment.Production && application.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Production for debug build.")
            } else if (environment === Environment.Sandbox && !application.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Sandbox for release build.")
            }

            val primaryConfig = PrimaryConfig(projectKey, launchMode, environment)
            val storeConfig = StoreConfig(store, shouldConsumePurchases)
            val loggerConfig = LoggerConfig(logLevel, logTag)
            val networkConfig = NetworkConfig()

            return QonversionConfig(
                application,
                primaryConfig,
                storeConfig,
                loggerConfig,
                networkConfig,
                cacheLifetime
            )
        }
    }
}
