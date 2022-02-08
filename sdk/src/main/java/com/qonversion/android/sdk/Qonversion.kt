package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.internal.QonversionInternal
import com.qonversion.android.sdk.internal.di.DependencyInjection
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

interface Qonversion {

    companion object {

        private var backingInstance: Qonversion? = null

        /**
         * Use this variable to get a current initialized instance of the Qonversion SDK.
         * Please, use the variable only after calling Qonversion.initialize().
         * Otherwise, trying to access the variable will cause an exception.
         * @return Current initialized instance of the Qonversion SDK.
         * @throws [QonversionException] with [ErrorCode.NotInitialized]
         */
        @JvmStatic
        val sharedInstance: Qonversion
            get() = backingInstance ?: throw QonversionException(ErrorCode.NotInitialized)

        /**
         * An entry point to use Qonversion SDK. Call to initialize Qonversion SDK with required and extra configs.
         * The function is the best way to set additional configs you need to use Qonversion SDK.
         * You still have an option to set a part of additional configs later via calling separated setters.
         * @param config a config that contains key SDK settings.
         * Call [QonversionConfig.Builder.build] to configure and create a QonversionConfig instance.
         * @return Initialized instance of the Qonversion SDK.
         */
        @JvmStatic
        fun initialize(config: QonversionConfig): Qonversion {
            DependencyInjection.init(config.application)
            return QonversionInternal(config, DependencyInjection).also {
                backingInstance = it
            }
        }
    }

    /**
     * Call to set an [Environment].
     * You may call this function to set an environment separately from [Qonversion.initialize]
     * Call this function only in case you are sure you need to set an environment after the SDK initialization.
     * Otherwise, set an environment via [Qonversion.initialize].
     * @param environment an environment.
     */
    fun setEnvironment(environment: Environment)

    /**
     * Use this function to change the level of the logs that the SDK prints.
     * You may set log level both after Qonversion SDK initializing with [Qonversion.setLogLevel] and while Qonversion initializing with [Qonversion.initialize]
     * See [LogLevel] for details.
     * @param logLevel a preferred log level.
     */
    fun setLogLevel(logLevel: LogLevel)

    /**
     * Use this function to set the log tag that the Qonversion SDK will print with every log message.
     * For example, you can use it to filter the Qonversion SDK logs and your app own logs together.
     * You may set log tag both after Qonversion SDK initializing with [Qonversion.setLogTag] and while Qonversion initializing with [Qonversion.initialize]
     * @param logTag a preferred log tag.
     */
    fun setLogTag(logTag: String)

    /**
     * Use this function to set the cache lifetime.
     * Qonversion SDK uses this value in case when user's internet connection is not stable.
     * For example, you can use it if your app can be used without the internet connection.
     * Set the cache lifetime as long as possible,
     * so you will not affect user experience while using your app offline.
     * You may set cache lifetime both after Qonversion SDK initializing with [Qonversion.setCacheLifetime] and while Qonversion initializing with [Qonversion.initialize]
     * @param cacheLifetime a preferred cache lifetime.
     */
    fun setCacheLifetime(cacheLifetime: CacheLifetime)

    /**
     * Call this function when you are done with the current instance of the Qonversion SDK.
     * Please, make sure you have a reason to finish the current instance and initialize the new one.
     * Initializing a new (not the first) instance of the Qonversion SDK is not necessary
     * for the most part of use cases.
     */
    fun finish()

    /**
     * Add property value for the current user to use it then for segmentation or analytics
     * as well as to provide it to third-party platforms.
     * This method consumes only defined user properties. In order to pass custom property
     * consider using [setCustomUserProperty] method.
     * You can either pass multiple properties at once using [setUserProperties] method.
     * @param property defined user attribute
     * @param value nonempty value for the given property
     */
    fun setUserProperty(property: UserProperty, value: String)

    /**
     * Add property value for the current user to use it then for segmentation or analytics
     * as well as to provide it to third-party platforms.
     * This method consumes custom user properties. In order to pass defined property
     * consider using [setUserProperty] method.
     * You can either pass multiple properties at once using [setUserProperties] method.
     * @param key nonempty key for user property consisting of letters A-Za-z, numbers, and symbols _.:-
     * @param value nonempty value for the given property
     */
    fun setCustomUserProperty(key: String, value: String)

    /**
     * Add a property value for the current user to use it then for segmentation or analytics
     * as well as to provide it to third-party platforms.
     * This method consumes both defined and custom user properties. Consider using
     * [UserPropertiesBuilder] to prepare a properties map. You are also able to create it
     * on your own using a custom key for a custom property or [UserProperty.code] as the key for
     * a Qonversion defined property .
     * In order to pass a single property consider using [setCustomUserProperty] method for
     * a custom key and [setUserProperty] for a defined one.
     * @param userProperties map of nonempty key-value pairs of user properties
     */
    fun setUserProperties(userProperties: Map<String, String>)
}
