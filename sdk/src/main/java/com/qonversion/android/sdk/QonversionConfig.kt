package com.qonversion.android.sdk

import android.content.Context
import android.util.Log
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.utils.isDebuggable

data class QonversionConfig internal constructor(
    val context: Context,
    val projectKey: String,
    val environment: Environment
) {

    data class Builder(
        private val context: Context,
        private val projectKey: String,
        private val mode: String,
        private val store: String,
    ) {
        internal var environment = Environment.PRODUCTION

        fun setEnvironment(environment: Environment): Builder = apply {
            this.environment = environment
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
            return QonversionConfig(context, projectKey, environment)
        }
    }
}
