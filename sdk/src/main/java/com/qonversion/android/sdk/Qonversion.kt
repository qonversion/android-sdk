package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.QonversionInternal
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
         * @throws QonversionException with [ErrorCode.NotInitialized]
         */
        @JvmStatic
        val sharedInstance: Qonversion
            get() = backingInstance ?: throw QonversionException(ErrorCode.NotInitialized)

        /**
         * An entry point to use Qonversion SDK. Call to initialize Qonversion SDK with required and extra configs.
         * The function is the best way to set additional configs you need to use Qonversion SDK.
         * You still have an option to set a part of additional configs later via calling separated setters.
         * @param config - a config that contains key SDK settings.
         * Call [QonversionConfig.Builder.build] to configure and create a QonversionConfig instance.
         * @return Initialized instance of the Qonversion SDK.
         */
        @JvmStatic
        fun initialize(config: QonversionConfig): Qonversion {
            return QonversionInternal(config).also {
                backingInstance = it
            }
        }
    }

    /**
     * Call to set an [Environment].
     * You may call this function to set an environment separately from [Qonversion.initialize]
     * Call this function only in case you are sure you need to set an environment after [Qonversion.initialize].
     * Otherwise, set an environment via [Qonversion.initialize].
     * @param environment - an environment.
     */
    fun setEnvironment(environment: Environment)

    /**
     * Use this function to change the level of logs that Qonversion SDK will print.
     * See [LogLevel] for details.
     * @param logLevel - a preferred log level.
     */
    fun setLogLevel(logLevel: LogLevel)

    /**
     * Use this function to set the log tag that Qonversion SDK will print with every log message.
     * You can use it, for example, to filter Qonversion SDK logs and your App own logs together.
     * @param logTag - a preferred log tag.
     */
    fun setLogTag(logTag: String)

    /**
     * Use this function to set the cache lifetime.
     * Qonversion SDK uses this value in case when user's internet connection is not stable.
     * You can use it, for example, if your App could be used without an internet connection.
     * Set the cache lifetime as long as possible,
     * so you will not affect UX for users using your App offline.
     * @param cacheLifetime: - a preferred cache life time.
     */
    fun setCacheLifetime(cacheLifetime: CacheLifetime)

    /**
     * Call this function when you are done with the current instance of Qonversion SDK.
     * Please, make sure you have a reason to finish the current instance and initialize the new one.
     * Initializing a new (not the first) instance of QonversionSDK is not necessary
     * for the most part of use cases.
     */
    fun finish()
}
