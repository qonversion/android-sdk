package com.qonversion.android.sdk

import android.content.Context
import android.util.Log
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.Store
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.utils.isDebuggable

private const val DEFAULT_LOG_TAG = "Qonversion"

data class QonversionConfig internal constructor(
    val context: Context,
    val projectKey: String,
    val launchMode: LaunchMode,
    val store: Store,
    val environment: Environment,
    val logLevel: LogLevel,
    val logTag: String,
    val backgroundCacheLifetime: CacheLifetime,
    val shouldConsumePurchases: Boolean
) {

    data class Builder(
        private val context: Context,
        private val projectKey: String,
        private val launchMode: LaunchMode,
        private val store: Store = Store.GOOGLE_PLAY
    ) {
        internal var environment = Environment.PRODUCTION
        internal var logLevel = LogLevel.Info
        internal var logTag = DEFAULT_LOG_TAG
        internal var backgroundCacheLifetime = CacheLifetime.THREE_DAYS
        internal var shouldConsumePurchases = true

        fun setEnvironment(environment: Environment): Builder = apply {
            this.environment = environment
        }

        fun setBackgroundCacheLifetime(backgroundCacheLifetime: CacheLifetime): Builder = apply {
            this.backgroundCacheLifetime = backgroundCacheLifetime
        }

        fun setLogLevel(logLevel: LogLevel): Builder = apply {
            this.logLevel = logLevel
        }

        fun setLogTag(logTag: String): Builder = apply {
            this.logTag = logTag
        }

        fun setShouldConsumePurchases(shouldConsumePurchases: Boolean): Builder = apply {
            this.shouldConsumePurchases = shouldConsumePurchases
        }

        fun build(): QonversionConfig {
            if (projectKey.isBlank()) {
                throw QonversionException(ErrorCode.ConfigPreparation, "Project key is empty")
            }
            if (environment === Environment.PRODUCTION && context.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Production for debug build.")
            } else if (environment === Environment.SANDBOX && !context.isDebuggable) {
                Log.w("Qonversion", "Environment level is set to Sandbox for release build.")
            }
            return QonversionConfig(
                context,
                projectKey,
                launchMode,
                store,
                environment,
                logLevel,
                logTag,
                backgroundCacheLifetime,
                shouldConsumePurchases
            )
        }
    }
}
